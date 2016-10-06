package asobu.distributed.service

import akka.actor._
import akka.cluster.Cluster
import Action.UnrecognizedMessage
import akka.stream.Materializer
import akka.util.ByteString
import asobu.distributed.protocol.EndpointDefinition
import asobu.distributed.service.extractors.DRequestExtractor
import asobu.distributed.{DResult, DRequest}
import asobu.distributed.protocol.Prefix
import asobu.distributed._
import play.api.http.HttpEntity
import play.api.mvc.{AnyContent, ResponseHeader, Result}
import play.core.routing.RouteParams
import play.routes.compiler.Route
import scala.concurrent.Future
import scala.util.parsing.input.Positional

trait Action {
  type TMessage

  val extractor: DRequestExtractor[TMessage]

  def name: String

  def handlerActor()(implicit sys: ActorSystem): ActorRef = {
    assert(!Cluster(sys).selfRoles.isEmpty, "Endpoint must be declared in node with a role in an Akka cluster")
    sys.actorOf(Props(new RemoteHandler).withDeploy(Deploy.local), name + "_Handler")
  }

  def enricherDefinition: Option[RequestEnricherDefinition]

  def endpointDefinition(
    route: Route,
    prefix: Prefix,
    version: Option[Int]
  )(implicit sys: ActorSystem): EndpointDefinition = EndpointDefinition(
    prefix,
    route,
    handlerActor().path,
    Cluster(sys).selfRoles.head,
    enricherDefinition,
    version
  )

  class RemoteHandler extends Actor {
    import context.dispatcher
    import cats.instances.future._
    def receive: Receive = {
      case dr: DRequest @unchecked ⇒

        val tr = extractor.run(dr)
        val replyTo = sender
        tr.map { t ⇒
          backend(dr.headers, t).foreach(replyTo ! _)
        }

      case _ ⇒ sender ! UnrecognizedMessage
    }

  }

  def backend: (Headers, TMessage) ⇒ Future[DResult]

}

object Action {
  type Aux[TMessage0] = Action { type TMessage = TMessage0 }

  case object UnrecognizedMessage

}
