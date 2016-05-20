package asobu.distributed

import akka.actor.{ActorPath, ActorRef}
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.gateway.RoutesCompilerExtra._
import asobu.distributed.service.ActionExtractor.RemoteExtractor
import asobu.distributed.service.RemoteExtractorDef
import asobu.dsl.{Extractor, ExtractResult}
import play.api.mvc.{AnyContent, Request}
import play.core.routing.RouteParams
import play.routes.compiler.Route
import shapeless.{HNil, HList}

import scala.concurrent.ExecutionContext

/**
 * Endpoint definition by the remote handler
 */
trait EndpointDefinition {
  /**
   * type of the Message
   */
  type T //todo: add serializable bound
  val routeInfo: Route
  val prefix: Prefix
  def handlerActor: ActorPath
  def clusterRole: String
  def version: Option[Int]

  val defaultPrefix: String = {
    if (prefix.value.endsWith("/")) "" else "/"
  }

  val documentation: (String, String, String) = {
    val localPath = if (routeInfo.path.parts.isEmpty) "" else defaultPrefix + routeInfo.path.toString
    val pathInfo = prefix.value + localPath
    (routeInfo.verb.toString, pathInfo, routeInfo.call.toString)
  }

  def handlerPath = handlerActor.toStringWithoutAddress

  val id: String = {
    val (verb, path, _) = documentation
    s"$verb $path"
  }

  def remoteExtractor(implicit ex: ExecutionContext): RemoteExtractor[T]

}

object EndpointDefinition {
  type Aux[T0] = EndpointDefinition { type T = T0 }
}

@SerialVersionUID(1L)
case class EndpointDefImpl[LExtracted <: HList, LParam <: HList, LExtra <: HList](
    prefix: Prefix,
    routeInfo: Route,
    remoteExtractorDef: RemoteExtractorDef[LExtracted, LParam, LExtra],
    handlerActor: ActorPath,
    clusterRole: String,
    version: Option[Int] = None
) extends EndpointDefinition {

  type T = LExtracted

  def remoteExtractor(implicit ex: ExecutionContext): RemoteExtractor[T] = remoteExtractorDef.extractor
}

/**
 * Endpoint that takes no input at all, just match a route path
 */
case class NullaryEndpointDefinition(
    prefix: Prefix,
    routeInfo: Route,
    handlerActor: ActorPath,
    clusterRole: String,
    version: Option[Int] = None
) extends EndpointDefinition {

  type T = HNil
  def extract(routeParams: RouteParams, request: Request[AnyContent]): ExtractResult[T] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    ExtractResult.pure(HNil)
  }

  def remoteExtractor(implicit ec: ExecutionContext) = Extractor.empty[(RouteParams, Request[AnyContent])]
}
