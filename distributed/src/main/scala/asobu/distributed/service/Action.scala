package asobu.distributed.service
import akka.actor._
import akka.cluster.Cluster
import Action.{DistributedResult, DistributedRequest, UnrecognizedMessage}
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.{Headers, EndpointDefImpl, EndpointDefinition}
import asobu.dsl.Extractor
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.mvc.{ResponseHeader, Result, AnyContent}
import play.routes.compiler.Route

import scala.concurrent.Future
import scala.reflect.internal.util.NoPosition
import scala.util.parsing.input.Positional

trait Action {
  type TMessage

  val extractors: Extractors[TMessage]

  type ExtractedRemotely = extractors.LToSend

  def name: String

  def endpointDefinition(route: Route, prefix: Prefix, version: Option[Int])(implicit sys: ActorSystem): EndpointDefinition.Aux[ExtractedRemotely] = {
    def assertPositionalSerializable(p: Positional) =
      assert(Option(p.pos).fold(true)(_.isInstanceOf[Serializable]), s"${p} must have a serializable pos") //the dangerous thing
    assertPositionalSerializable(route)
    assertPositionalSerializable(route.call)
    assert(!Cluster(sys).selfRoles.isEmpty, "Endpoint must be declared in node with a role in an Akka cluster")
    val handlerActor = sys.actorOf(Props(new RemoteHandler).withDeploy(Deploy.local), name + "_Handler")

    EndpointDefImpl(prefix, route, extractors.remoteExtractorDef, handlerActor.path, Cluster(sys).selfRoles.head)
  }

  class RemoteHandler extends Actor {
    import context.dispatcher
    import cats.std.future._
    def receive: Receive = {
      case dr: DistributedRequest[extractors.LToSend] @unchecked ⇒

        val tr = extractors.localExtract(dr)
        val replyTo = sender
        tr.map { t ⇒
          backend(dr.headers, t).foreach(replyTo ! _)
        }

      case _ ⇒ sender ! UnrecognizedMessage
    }

  }

  def backend: (Headers, TMessage) ⇒ Future[DistributedResult]

}

object Action {
  type Aux[TMessage0] = Action { type TMessage = TMessage0 }

  case object UnrecognizedMessage

  case class HttpStatus(code: Int) extends AnyVal

  case class DistributedRequest[ExtractedT](
    extracted: ExtractedT,
    body: AnyContent,
    headers: Headers = Nil
  )

  case class DistributedResult(
      status: HttpStatus,
      headers: Headers = Nil,
      body: Array[Byte] = Array.empty
  ) {
    def toResult =
      Result(new ResponseHeader(status.code, headers.toMap), Enumerator(body))
  }

  object DistributedResult {

    implicit def from(r: Result): Future[DistributedResult] = {
      import scala.concurrent.ExecutionContext.Implicits.global
      (r.body run Iteratee.getChunks) map { chunks ⇒
        val body = chunks.toArray.flatten
        DistributedResult(HttpStatus(r.header.status), r.header.headers.toSeq, body)
      }
    }
  }

}
