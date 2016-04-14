package asobu.distributed.service

import akka.actor.{ActorSystem, ActorRef}
import akka.util.Timeout
import asobu.distributed.service.Action.{DistributedRequest, DistributedResult}
import asobu.distributed.{Headers, RequestExtractorDefinition, EndpointDefinition}
import asobu.dsl.{ExtractorFunctions, ExtractResult, Extractor}

import play.api.libs.json.{Reads, Json, Writes}
import play.api.mvc.{Results, Result}
import play.api.mvc.Results._
import shapeless.LabelledGeneric

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect._
import akka.pattern.ask
import Syntax._
import asobu.dsl.CatsInstances._
import scala.concurrent.ExecutionContext.Implicits.global

trait Syntax extends ExtractorFunctions {
  parent: Controller ⇒

  /**
   *
   * @param name
   * @param extrs
   * @param bk can also take [[ Extractor[T, Result] ]] or [[ Extractor[(Headers, T), Result] ]]
   * @return
   */
  def handle[T](
    name: String,
    extrs: ActionExtractor[T]
  )(bk: (Headers, T) ⇒ Future[DistributedResult])(
    implicit
    rc: EndpointsRegistryClient,
    ec: ExecutionContext,
    sys: ActorSystem
  ): (Action.Aux[T], Future[EndpointDefinition]) = {
    val name0 = name
    val action = new Action {
      val name = actionName(name0)
      type TMessage = T
      val extractors = extrs
      def backend = bk
    }
    (action, addAction(action))
  }

  def from = cats.sequence.sequenceRecord

  val noExtraFields = RequestExtractorDefinition.empty

  def process[T] = ActionExtractor.build[T]

  //the incoming type could be specific but it couldn't be inferred, so it's leaved as Any to avoid the need of respecifying the data type here
  def using(actor: ActorRef)(implicit at: Timeout, ec: ExecutionContext): Extractor[Any, Any] =
    Extractor.fromFunction { t ⇒
      ExtractResult.right(actor ? t)
    }

  def fromJsonBody[T: Reads: LabelledGeneric] = jsonBody.allFields

  def jsonBody[T: Reads] = BodyExtractor.json[T]

  def respond[A](result: Result): Extractor[A, Result] = respond(_ ⇒ result)

  def respond[A](f: A ⇒ Result): Extractor[A, Result] = Extractor(f)

  def respondJson[A](r: Results#Status)(implicit writes: Writes[A]): Extractor[A, Result] =
    respond(t ⇒ r.apply(Json.toJson(t)))

  implicit class SyntaxExtractorOps[A, B](self: Extractor[A, B]) {
    def expect[C: ClassTag]: Extractor[A, C] = {
      self.flatMapF {
        case t: C if classTag[C].runtimeClass.isInstance(t) ⇒ ExtractResult.pure(t)
        case _ ⇒
          ExtractResult.left(InternalServerError(s"unexpected response from backend, was expecting ${classTag[C].runtimeClass.getTypeName}")) //todo: globalizing un expected result from backend error
      }
    }

    def >>[C](extractor: Extractor[B, C]): Extractor[A, C] = self.andThen(extractor)
  }

  implicit def toBackend[T](extractor: Extractor[T, Result]): (Headers, T) ⇒ Future[DistributedResult] =
    extractor.compose((p: (Headers, T)) ⇒ ExtractResult.pure(p._2))

  implicit def toBackendWHeaders[T](extractor: Extractor[(Headers, T), Result]): (Headers, T) ⇒ Future[DistributedResult] =
    (headers: Headers, t: T) ⇒ extractor.
      run((headers, t)).v.fold(identity, identity).flatMap(DistributedResult.from(_))

}

object Syntax {

}
