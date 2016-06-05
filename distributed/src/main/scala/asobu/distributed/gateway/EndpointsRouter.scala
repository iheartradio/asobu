package asobu.distributed.gateway

import akka.agent.Agent
import play.api.mvc.{RequestHeader, Result}
import play.api.mvc.Results.NotFound
import scala.concurrent.{Future, ExecutionContext}

object EndpointsRouter {
  def apply()(implicit ex: ExecutionContext) = new EndpointsRouter()
}

/**
 * Route http requests to endpoints' handler method
 */
class EndpointsRouter(
    onNotFound: RequestHeader ⇒ Future[Result] = req ⇒ Future.successful(
      NotFound(s"Action or remote endpoints not found for ${req.path}")
    )
)(implicit ex: ExecutionContext) {
  type Handler = PartialFunction[RequestHeader, Future[Result]]

  val endpointsAgent = Agent[(Handler, List[Endpoint])]((PartialFunction(onNotFound), Nil))

  def update(endpoints: List[Endpoint]) = {
    def toPartial(endpoint: Endpoint): Handler = {
      case req @ endpoint(requestParams) ⇒ endpoint.handle(requestParams, req)
    }

    //todo: improve performance by doing a prefix search first
    val endpointsHandlerPartial = (endpoints.map(toPartial) :+ PartialFunction(onNotFound))
      .reduce(_ orElse _)

    endpointsAgent.alter((endpointsHandlerPartial, endpoints))
  }

  def handle(req: RequestHeader) = {
    val (handler, _) = endpointsAgent.get()
    handler(req)
  }
}

