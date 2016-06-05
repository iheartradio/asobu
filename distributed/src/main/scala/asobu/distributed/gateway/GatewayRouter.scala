package asobu.distributed.gateway

import javax.inject.Inject

import akka.util.Timeout
import play.api.http.HttpErrorHandler
import play.api.mvc.{Result, Action, Results}, Results.InternalServerError

import akka.pattern.ask
import play.api.routing.Router
import play.api.routing.Router.Routes
import concurrent.duration._
import scala.concurrent.ExecutionContext

/**
 * Proxy router for play
 */
class GatewayRouter @Inject() (endpointsRouter: EndpointsRouter)(implicit ec: ExecutionContext) extends Router {
  implicit val ao: Timeout = 30.seconds //todo: make this configurable

  val handleAll = Action.async { implicit req ⇒
    endpointsRouter.handle(req).collect {
      case r: Result ⇒ r
      case _         ⇒ InternalServerError("non-result returned by gateway")
    }
  }

  def routes: Routes = {
    case _ ⇒ handleAll
  }

  def withPrefix(prefix: String): Router = this //for now we don't support custom prefix here.

  def documentation: Seq[(String, String, String)] = Nil //we don't have any documentation on startup. todo: Maybe provide the documentation from the endpoints in the future if needed.
}

