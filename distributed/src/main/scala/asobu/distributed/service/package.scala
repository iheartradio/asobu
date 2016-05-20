package asobu.distributed

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.util.Timeout
import asobu.distributed.gateway.Endpoint.Prefix
import play.api.libs.json.JsObject
import play.routes.compiler.Route

import scala.util.Try

package object service {
  def init(controllers: EndpointsRegistryClient ⇒ List[Controller])(implicit
    system: ActorSystem,
    ao: Timeout,
    prefix: Prefix = Prefix("/"),
    buildNumber: Option[BuildNumber] = None,
    apiDocGenerator: (Prefix, Seq[Route]) ⇒ Option[JsObject] = (_, _) ⇒ None): Unit = {

    val registry: EndpointsRegistry = DefaultEndpointsRegistry()

    implicit val rec: EndpointsRegistryClient = EndpointsRegistryClientImp(
      registry,
      prefix      = prefix,
      buildNumber = buildNumber
    )

    val initControllers = Try(controllers(rec))
    val apiDocReporter = ApiDocumentationReporter(registry)(routes ⇒ apiDocGenerator(prefix, routes))

    initControllers.foreach(apiDocReporter.report)

    initControllers.recover {
      case e: Throwable ⇒
        system.log.error(e, s"Cannot initialize controllers, Exiting")
        system.terminate()
    }

  }
}
