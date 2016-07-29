package asobu.distributed.gateway

import akka.agent.Agent
import com.google.inject.{Singleton, Inject}
import play.api.mvc.{RequestHeader, Result}
import play.api.mvc.Results.NotFound
import scala.concurrent.{Future, ExecutionContext}

object EndpointsRouter {
  def apply()(implicit ex: ExecutionContext) = new EndpointsRouter()
}

/**
 * Route http requests to endpoints' handler method
 */
@Singleton
class EndpointsRouter(onNotFound: RequestHeader ⇒ Future[Result])(implicit ex: ExecutionContext) {
  //needed for injection
  @Inject def this()(implicit ex: ExecutionContext) = this(req ⇒ Future.successful(
    NotFound(s"Action or remote endpoints not found for ${req.path}")
  ))

  type Handler = PartialFunction[RequestHeader, Future[Result]]

  val endpointsAgent = Agent[(Handler, List[Endpoint])]((PartialFunction(onNotFound), Nil))

  def update(endpoints: List[Endpoint]): Future[(Handler, List[Endpoint])] = {
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

