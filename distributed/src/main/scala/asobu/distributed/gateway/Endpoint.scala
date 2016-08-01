package asobu.distributed.gateway

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{PoisonPill, ActorRef, ActorRefFactory}
import akka.routing.RoundRobinGroup
import akka.util.Timeout
import asobu.distributed.CustomRequestExtractorDefinition.Interpreter
import asobu.distributed.service.Action.{DistributedResult, DistributedRequest}
import asobu.distributed.EndpointDefinition
import asobu.distributed.service.ActionExtractor.RemoteExtractor
import play.api.mvc.Results._
import play.api.mvc.{Result, AnyContent, Request}
import play.core.routing
import play.core.routing.Route.ParamsExtractor
import play.core.routing.RouteParams
import play.routes.compiler.{DynamicPart, PathPart, StaticPart}
import cats.std.all._
import scala.concurrent.{ExecutionContext, Future, duration}, duration._

trait EndpointRoute {
  def unapply(requestHeader: Request[AnyContent]): Option[RouteParams]
}

trait EndpointHandler {
  def handle(routeParams: RouteParams, request: Request[AnyContent]): Future[Result]
}

/**
 *
 * @param definition of the endpoint provided by the service side
 * @param bridgeProps a factory that creates the prop for an bridge actor between
 *                    gateway router and actual handling service actor
 */
case class Endpoint(
    definition: EndpointDefinition,
    bridgeProps: HandlerBridgeProps = HandlerBridgeProps.default
)(implicit
  arf: ActorRefFactory,
    ec: ExecutionContext,
    interpreter: Interpreter) extends EndpointRoute with EndpointHandler {

  type T = definition.T

  import definition._
  implicit val ak: Timeout = 10.minutes //todo: find the right place to configure this

  private val handlerRef: ActorRef = {
    val props = bridgeProps(handlerPath, definition.clusterRole)
    //a random name allows some redundancy in this router.
    val bridgeActorName = definition.clusterRole + handlerActor.name.replace("$", "") + ThreadLocalRandom.current().nextInt(1000)
    arf.actorOf(props, bridgeActorName)
  }

  def shutdown(): Unit = if (handlerRef != handlerActor) handlerRef ! PoisonPill

  def unapply(request: Request[AnyContent]): Option[RouteParams] = {
    // queryString's parser parses an empty string as Map("" -> Seq()), so we replace query strings made up of all empty values
    // with an empty map
    // https://github.com/playframework/playframework/blob/master/framework/src/play/src/main/scala/play/core/parsers/FormUrlEncodedParser.scala#L23
    routeExtractors.unapply(request).map { params ⇒
      if (params.queryString.forall {
        case (key, values) ⇒
          key.trim.isEmpty && values.forall(_.trim.isEmpty)
      }) {
        params.copy(queryString = Map.empty)
      } else params
    }
  }

  //todo: think of a way to get rid of the ask below, e.g. create an new one-time actor for handling (just like ask),
  // or have distributed request have the reply to Address and then send it to handlerRef as the implicit sender.
  def handle(routeParams: RouteParams, request: Request[AnyContent]): Future[Result] = {
    import akka.pattern.ask
    import ExecutionContext.Implicits.global

    def handleMessageWithBackend(t: T): Future[Result] = {
      (handlerRef ? DistributedRequest(t, request.body, request.headers.headers)).collect {
        case r: DistributedResult ⇒ r.toResult
        case m                    ⇒ InternalServerError(s"Unsupported result from backend ${m.getClass}")
      }
    }
    val message = extractor.run((routeParams, request))
    message.fold[Future[Result]](
      Future.successful,
      handleMessageWithBackend
    ).flatMap(identity)
  }

  lazy val extractor: RemoteExtractor[T] = definition.remoteExtractor(interpreter)

  private lazy val routeExtractors: ParamsExtractor = {
    val localParts = if (routeInfo.path.parts.nonEmpty) StaticPart(defaultPrefix) +: routeInfo.path.parts else Nil
    routing.Route(routeInfo.verb.value, routing.PathPattern(toCPart(StaticPart(prefix.value) +: localParts)))
  }

  private def toCPart(parts: Seq[PathPart]): Seq[routing.PathPart] = parts map {
    case DynamicPart(n, c, e) ⇒ routing.DynamicPart(n, c, e)
    case StaticPart(v)        ⇒ routing.StaticPart(v)
  }

}

object Endpoint {
  @SerialVersionUID(1L)
  class Prefix private (val value: String) extends AnyVal

  object Prefix {
    val root = apply("/")
    def apply(value: String): Prefix = {
      assert(value.startsWith("/"), "prefix must start with /")
      new Prefix(value)
    }
  }
}
