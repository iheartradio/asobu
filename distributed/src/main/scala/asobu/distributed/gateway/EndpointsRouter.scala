package asobu.distributed.gateway

import akka.actor.{Props, Actor}
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.gateway.EndpointsRouter.Update
import play.api.mvc.RequestHeader
import play.api.mvc.Results.NotFound
class EndpointsRouter extends Actor {

  def receive: Receive = handling(Nil)

  def handling(endpoints: List[Endpoint]): Receive = {
    def toPartial(endpoint: Endpoint): Receive = {
      case req @ endpoint(requestParams) ⇒
        val rf = endpoint.handle(requestParams, req)
        import context.dispatcher
        val replyTo = sender
        rf.foreach(replyTo ! _)
    }

    val endpointsHandlerPartial = //todo: improve performance by doing a prefix search first
      endpoints.map(toPartial).foldLeft(PartialFunction.empty: Receive)(_ orElse _)

    ({
      case Update(eps) ⇒
        context become handling(eps)
    }: Receive) orElse endpointsHandlerPartial orElse {
      case req: RequestHeader ⇒ sender ! NotFound(s"Action or remote endpoints not found for ${req.path}")
    }
  }

}

object EndpointsRouter {

  def props = Props(new EndpointsRouter)
  case class Update(endpoints: List[Endpoint])
}
