package asobu.distributed.protocol

import akka.actor.ActorPath
import asobu.distributed.RequestEnricherDefinition
import play.routes.compiler._
import EndpointDefinition._

@SerialVersionUID(1L)
case class EndpointDefinition(
  prefix: Prefix,
  verb: Verb,
  path: Seq[PathPart],
  call: String,
  handlerAddress: HandlerAddress,
  clusterRole: String,
  enricherDef: Option[RequestEnricherDefinition] = None,
  version: Option[Int] = None
)

@SerialVersionUID(1L)
case class Verb(value: String)

sealed trait PathPart

@SerialVersionUID(1L)
case class StaticPathPart(value: String) extends PathPart

@SerialVersionUID(1L)
case class DynamicPathPart(name: String, constraint: String, encode: Boolean) extends PathPart

@SerialVersionUID(1L)
case class HandlerAddress(value: String) extends AnyVal

@SerialVersionUID(1L)
class Prefix private (val value: String) extends AnyVal

object EndpointDefinition {

  implicit class EndpointDefinitionOps(val ed: EndpointDefinition) {
    import ed._
    lazy val defaultPrefix: String = {
      if (prefix.value.endsWith("/")) "" else "/"
    }

    lazy val pathPattern = PathPattern(path.map {
      case StaticPathPart(v)        ⇒ StaticPart(v)
      case DynamicPathPart(n, c, e) ⇒ DynamicPart(n, c, e)
    })

    lazy val documentation: (String, String, String) = {
      val localPath = if (pathPattern.parts.isEmpty) "" else defaultPrefix + pathPattern.toString
      val pathInfo = prefix.value + localPath
      (verb.toString, pathInfo, call)
    }

    def handlerActorPath = ActorPath.fromString(handlerAddress.value)

    def handlerPath = handlerActorPath.toStringWithoutAddress

    lazy val id: String = {
      val (verb, path, _) = documentation
      s"$verb $path"
    }
  }

  implicit class actorPathToHandlerAddress(actorPath: ActorPath) {
    def handlerAddress: HandlerAddress = HandlerAddress(actorPath.toStringWithAddress(actorPath.address))
  }

  def apply(
    prefix: Prefix,
    route: Route,
    handlerAddress: HandlerAddress,
    clusterRole: String,
    enricherDef: Option[RequestEnricherDefinition],
    version: Option[Int]
  ): EndpointDefinition =
    EndpointDefinition(
      prefix,
      Verb(route.verb.value),
      route.path.parts.map {
        case StaticPart(v)        ⇒ StaticPathPart(v)
        case DynamicPart(n, c, e) ⇒ DynamicPathPart(n, c, e)
      },
      route.call.toString(),
      handlerAddress,
      clusterRole,
      enricherDef,
      version
    )

}

object Prefix {
  val root = apply("/")
  def apply(value: String): Prefix = {
    assert(value.startsWith("/"), "prefix must start with /")
    new Prefix(value)
  }
}
