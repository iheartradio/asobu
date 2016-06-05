package asobu.distributed.service

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.util.Timeout
import asobu.distributed.EndpointsRegistryUpdater.{Added, AddDoc}
import asobu.distributed.{EndpointsRegistryUpdater, EndpointsRegistry}
import play.api.libs.json.JsObject
import play.routes.compiler.Route
import akka.pattern.ask
import scala.concurrent.{Future, ExecutionContext}

case class ApiDocumentationReporter(registry: EndpointsRegistry)(
    generator: Seq[Route] ⇒ Option[JsObject]
)(
    implicit
    system: ActorSystem,
    ao: Timeout
) {

  lazy val clientActor = system.actorOf(EndpointsRegistryUpdater.props(registry), "asobu-endpoint-registry-client-for-doc")

  lazy val roleO = Cluster(system).selfRoles.headOption

  def report(controllers: Seq[Controller])(implicit ec: ExecutionContext): Future[Option[JsObject]] = {
    val allRoutes = controllers.flatMap(_.routes)
    val addO = for {
      role ← roleO
      doc ← generator(allRoutes)
    } yield AddDoc(role, doc)

    addO.fold[Future[Option[JsObject]]](Future.successful(None)) { add ⇒
      (clientActor ? add) collect {
        case Added(_) ⇒ addO.map(_.doc)
      }
    }
  }
}
