package asobu.distributed.gateway

import akka.actor._
import akka.cluster.ddata.Replicator._
import asobu.distributed.EndpointsRegistry
import asobu.distributed.gateway.ApiDocumentationRegistry.Retrieve
import play.api.libs.json.{JsObject, Json}

class ApiDocumentationRegistry(endpointsRegistry: EndpointsRegistry) extends Actor with ActorLogging {
  import endpointsRegistry._

  def receive: Receive = {
    case Retrieve ⇒
      replicator ! Get(EndpointsDocsKey, ReadLocal, Some(sender))

    case g @ GetSuccess(EndpointsDocsKey, Some(replyTo: ActorRef)) ⇒
      val allDoc = g.get(EndpointsDocsKey).entries.values.toList.map(
        Json.parse(_).as[JsObject]
      ).foldLeft(Json.obj())(_ deepMerge _)
      replyTo ! allDoc

    case GetFailure(EndpointsDocsKey, Some(replyTo: ActorRef)) ⇒
      log.error("Failed to retrieve distributed API documentation")
      replyTo ! Json.obj("error" → "failed to retrieve API documentation")

    case NotFound(EndpointsDocsKey, Some(replyTo: ActorRef)) ⇒
      replyTo ! Json.obj()

  }
}

object ApiDocumentationRegistry {
  case object Retrieve
  def props(registry: EndpointsRegistry) = Props(new ApiDocumentationRegistry(registry))
}
