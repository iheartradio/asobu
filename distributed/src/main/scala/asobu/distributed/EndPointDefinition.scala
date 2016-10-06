package asobu.distributed

import akka.actor.{ActorPath, ActorRef}
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.gateway.RoutesCompilerExtra._
import asobu.distributed.gateway.enricher.Interpreter
import asobu.dsl.{RequestExtractor, Extractor, ExtractResult}
import play.api.mvc.{AnyContent, Request}
import play.core.routing.RouteParams
import play.routes.compiler.{PathPattern, HttpVerb, Route}
import shapeless.{HNil, HList}

import scala.concurrent.ExecutionContext

/**
 * Endpoint definition by the remote handler
 */
sealed trait EndpointDefinition {
  def verb: HttpVerb
  def path: PathPattern
  def call: String
  def prefix: Prefix //todo: move prefix to Endpoint set
  def handlerActor: ActorPath
  def clusterRole: String
  def version: Option[Int] //todo: move version to Endpoint set

  val defaultPrefix: String = {
    if (prefix.value.endsWith("/")) "" else "/"
  }

  val documentation: (String, String, String) = {
    val localPath = if (path.parts.isEmpty) "" else defaultPrefix + path.toString
    val pathInfo = prefix.value + localPath
    (verb.toString, pathInfo, call)
  }

  def handlerPath = handlerActor.toStringWithoutAddress

  val id: String = {
    val (verb, path, _) = documentation
    s"$verb $path"
  }
}

@SerialVersionUID(2L)
case class EndpointDefSimple(
  prefix: Prefix,
  verb: HttpVerb,
  path: PathPattern,
  call: String,
  handlerActor: ActorPath,
  clusterRole: String,
  version: Option[Int] = None
) extends EndpointDefinition

@SerialVersionUID(2L)
case class EndpointDefWithEnrichment(
  prefix: Prefix,
  verb: HttpVerb,
  path: PathPattern,
  call: String,
  enricherDef: RequestEnricherDefinition,
  handlerActor: ActorPath,
  clusterRole: String,
  version: Option[Int] = None
) extends EndpointDefinition

object EndpointDefinition {
  def apply(
    prefix: Prefix,
    route: Route,
    handlerActor: ActorPath,
    clusterRole: String,
    version: Option[Int] = None
  ): EndpointDefinition =
    EndpointDefSimple(
      prefix,
      route.verb,
      route.path,
      route.call.toString(),
      handlerActor,
      clusterRole,
      version
    )

  def apply(
    prefix: Prefix,
    route: Route,
    enricherDef: RequestEnricherDefinition,
    handlerActor: ActorPath,
    clusterRole: String,
    version: Option[Int]
  ): EndpointDefinition =
    EndpointDefWithEnrichment(
      prefix,
      route.verb,
      route.path,
      route.call.toString(),
      enricherDef,
      handlerActor,
      clusterRole,
      version
    )

}
