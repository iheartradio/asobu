package asobu.distributed.service

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import asobu.distributed.service.extractors.DRequestExtractor
import asobu.distributed.protocol.{DResult, DRequest}
import asobu.distributed.{RequestEnricherDefinition, Headers}
import asobu.dsl.{ExtractResult, Extractor, ExtractorFunctions}
import play.api.libs.json.{Json, Reads, Writes}
import play.api.mvc.{Result, Results}
import play.api.mvc.Results._
import shapeless.LabelledGeneric

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect._
import akka.pattern.ask
import akka.stream.Materializer
import asobu.dsl.CatsInstances._
import scala.language.implicitConversions
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
    extrs: DRequestExtractor[T]
  )(bk: (Headers, T) ⇒ Future[DResult]): Action.Aux[T] = handle(name, extrs, None)(bk)

  def handle[T](
    name: String,
    enricherDef: RequestEnricherDefinition,
    extrs: DRequestExtractor[T]
  )(bk: (Headers, T) ⇒ Future[DResult]): Action.Aux[T] = handle(name, extrs, Some(enricherDef))(bk)

  private def handle[T](
    name: String,
    extrs: DRequestExtractor[T],
    enricherDef: Option[RequestEnricherDefinition]
  )(bk: (Headers, T) ⇒ Future[DResult]): Action.Aux[T] = {
    val name0 = name
    new Action {
      val enricherDefinition = enricherDef
      val name = actionName(name0)
      type TMessage = T
      val extractor = extrs
      def backend = bk
    }
  }

  def from = cats.sequence.sequenceRecord

  val noExtraFields = Extractor.empty[DRequest]

  def process[T] = DRequestExtractor.build[T]

  //the incoming type could be specific but it couldn't be inferred, so it's leaved as Any to avoid the need of respecifying the data type here
  def using(actor: ActorRef)(implicit at: Timeout, ec: ExecutionContext): Extractor[Any, Any] =
    Extractor.of { t ⇒
      ExtractResult.right(actor ? t)
    }

  def fromJsonBody[T: Reads: LabelledGeneric] = jsonBody.allFields

  def jsonBody[T: Reads] = BodyExtractors.json[T]

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

  implicit def toBackend[T](extractor: Extractor[T, Result])(implicit mat: Materializer): (Headers, T) ⇒ Future[DResult] =
    extractor.compose((p: (Headers, T)) ⇒ ExtractResult.pure(p._2))

  implicit def toBackendWHeaders[T](extractor: Extractor[(Headers, T), Result])(implicit mat: Materializer): (Headers, T) ⇒ Future[DResult] =
    (headers: Headers, t: T) ⇒ extractor.
      run((headers, t)).fold(identity, identity).flatMap(DResult.from(_))

}

