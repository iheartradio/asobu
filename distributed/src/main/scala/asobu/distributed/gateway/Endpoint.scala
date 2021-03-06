package asobu.distributed.gateway

import java.util.concurrent.ThreadLocalRandom

import akka.actor.{PoisonPill, ActorRef, ActorRefFactory}
import akka.routing.RoundRobinGroup
import akka.util.Timeout
import asobu.distributed.protocol.EndpointDefinition
import asobu.distributed.protocol.{RequestParams, DRequest, DResult}
import asobu.distributed.gateway.enricher.Interpreter
import asobu.distributed._
import asobu.dsl.{Extractor, ExtractResult}
import play.api.mvc.Results._
import play.api.mvc._
import play.core.routing
import play.core.routing.Route.ParamsExtractor
import play.core.routing.RouteParams
import play.routes.compiler.{DynamicPart, PathPart, StaticPart}
import asobu.dsl.CatsInstances._
import scala.concurrent.{ExecutionContext, Future, duration}, duration._
import scala.reflect.ClassTag
import protocol._
import EndpointDefinition._

trait EndpointRoute {
  def unapply(requestHeader: Request[RawBuffer]): Option[RouteParams]
}

trait EndpointHandler {
  def handle(gateWayRequest: GateWayRequest): Future[Result]
}

/**
 * @param definition of the endpoint provided by the service side
 * @param bridgeProps a factory that creates the prop for an bridge actor between
 *                    gateway router and actual handling service actor
 */
abstract class Endpoint(
    val definition: EndpointDefinition,
    bridgeProps: HandlerBridgeProps = HandlerBridgeProps.default
)(
    implicit
    arf: ActorRefFactory,
    ec: ExecutionContext
) extends EndpointRoute with EndpointHandler {
  implicit val ak: Timeout = 10.minutes //todo: find the right place to configure this

  private val handlerRef: ActorRef = {
    val props = bridgeProps(definition.handlerPath, definition.clusterRole)
    //a random name allows some redundancy in this router.
    val bridgeActorName = definition.clusterRole + definition.handlerActorPath.name.replace("$", "") + ThreadLocalRandom.current().nextInt(1000)
    arf.actorOf(props, bridgeActorName)
  }

  def shutdown(): Unit = handlerRef ! PoisonPill

  def unapply(request: Request[RawBuffer]): Option[RouteParams] = {
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
  def handle(request: GateWayRequest): Future[Result] = {
    import akka.pattern.ask

    val dRequestER: ExtractResult[DRequest] = {
      val pathParamsErrors = request.routeParam.path.collect {
        case (key, Left(e)) ⇒ (key, e)
      }

      if (pathParamsErrors.nonEmpty)
        ExtractResult.left(BadRequest("unable to parse path params: " + pathParamsErrors.mkString(",")))
      else
        ExtractResult.pure {
          val pathParams = request.routeParam.path.collect { case (key, Right(v)) ⇒ (key, v) }
          DRequest(
            RequestParams(pathParams, request.routeParam.queryString),
            request.request
          )
        }
    }

    val finalResult: ExtractResult[Result] = for {
      drRaw ← dRequestER
      dr ← enricher(drRaw)
      result ← ExtractResult.fromEitherF((handlerRef ? dr).map {
        case r: DResult ⇒ Right(DResult.toResult(r)) //todo
        case m          ⇒ Left(InternalServerError(s"Unsupported result from backend ${m.getClass}"))
      })
    } yield result

    finalResult.fold(identity, identity)
  }

  private lazy val routeExtractors: ParamsExtractor = {
    val localParts = if (definition.path.nonEmpty) StaticPart(definition.defaultPrefix) +: definition.pathPattern.parts else Nil
    routing.Route(definition.verb.value, routing.PathPattern(toCPart(StaticPart(definition.prefix.value) +: localParts)))
  }

  private def toCPart(parts: Seq[PathPart]): Seq[routing.PathPart] = parts map {
    case DynamicPart(n, c, e) ⇒ routing.DynamicPart(n, c, e)
    case StaticPart(v)        ⇒ routing.StaticPart(v)
  }

  protected val enricher: RequestEnricher
}

object Endpoint {

  trait EndpointFactory {
    def apply(endpointDef: EndpointDefinition): Endpoint
  }

  object EndpointFactory {
    def apply[T <: RequestEnricherDefinition: Interpreter: ClassTag](bridgeProps: HandlerBridgeProps)(implicit
      arf: ActorRefFactory,
      ec: ExecutionContext): EndpointFactory = new EndpointFactoryImpl[T](bridgeProps)
  }

  class EndpointFactoryImpl[T <: RequestEnricherDefinition: Interpreter: ClassTag](bridgeProps: HandlerBridgeProps)(implicit
    arf: ActorRefFactory,
      ec: ExecutionContext) extends EndpointFactory {
    def apply(endpointDef: EndpointDefinition): Endpoint = new Endpoint(endpointDef, bridgeProps) {
      override protected val enricher: RequestEnricher =
        endpointDef.enricherDef.fold[RequestEnricher](Extractor(identity))(Interpreter.interpret[T])
    }
  }
}
