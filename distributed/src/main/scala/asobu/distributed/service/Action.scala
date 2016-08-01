package asobu.distributed.service

import akka.actor._
import akka.cluster.Cluster
import Action.{DistributedRequest, DistributedResult, UnrecognizedMessage}
import akka.stream.Materializer
import akka.util.ByteString
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.{EndpointDefImpl, EndpointDefinition, Headers}
import play.api.http.HttpEntity
import play.api.mvc.{AnyContent, ResponseHeader, Result}
import play.routes.compiler.Route
import scala.concurrent.Future
import scala.util.parsing.input.Positional

trait Action {
  type TMessage

  val extractors: ActionExtractor[TMessage]

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
      Result(new ResponseHeader(status.code, headers.toMap), HttpEntity.Strict(ByteString(body), None))
  }

  object DistributedResult {

    def from(result: Result)(implicit mat: Materializer): Future[DistributedResult] = {
      import scala.concurrent.ExecutionContext.Implicits.global
      result.body.consumeData.map { data ⇒
        DistributedResult(
          HttpStatus(result.header.status),
          result.header.headers.toSeq,
          data.toArray[Byte]
        )
      }
    }

  }

}
