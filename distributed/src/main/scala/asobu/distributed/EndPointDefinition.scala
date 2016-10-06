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

@SerialVersionUID(2L)
case class EndpointDefinition(
    prefix: Prefix,
    verb: HttpVerb,
    path: PathPattern,
    call: String,
    handlerActor: ActorPath,
    clusterRole: String,
    enricherDef: Option[RequestEnricherDefinition] = None,
    version: Option[Int] = None
) {

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

object EndpointDefinition {

  def apply(
    prefix: Prefix,
    route: Route,
    handlerActor: ActorPath,
    clusterRole: String,
    enricherDef: Option[RequestEnricherDefinition],
    version: Option[Int]
  ): EndpointDefinition =
    EndpointDefinition(
      prefix,
      route.verb,
      route.path,
      route.call.toString(),
      handlerActor,
      clusterRole,
      enricherDef,
      version
    )

}
