package asobu.distributed.protocol

import akka.actor.ActorPath
import asobu.distributed.RequestEnricherDefinition
import asobu.distributed.protocol.Prefix
import play.routes.compiler.{HttpVerb, PathPattern, Route}
import EndpointDefinition._

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

  case class Verb(value: String)

  sealed trait PathPath
  case class StaticPathPart(value: String) extends PathPath
  case class DynamicPathPart(name: String, constraint: String, encode: Boolean) extends PathPath
  case class HandlerAddress(value: String) extends AnyVal

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

@SerialVersionUID(1L)
class Prefix private (val value: String) extends AnyVal

object Prefix {
  val root = apply("/")
  def apply(value: String): Prefix = {
    assert(value.startsWith("/"), "prefix must start with /")
    new Prefix(value)
  }
}
