package asobu.distributed.util

import akka.actor.ActorRefFactory
import asobu.distributed.gateway.Endpoint.EndpointFactory
import asobu.distributed.gateway.HandlerBridgeProps
import asobu.distributed.gateway.enricher.DisabledInterpreter
import asobu.distributed.protocol.{StaticPathPart, PathPart, Prefix, EndpointDefinition}
import asobu.distributed.service.EndpointRoutesParser

import play.routes.compiler.{StaticPart, PathPattern, Route}

import scala.concurrent.ExecutionContext

object EndpointUtil {

  def parseEndpoints(routeString: String, prefix: Prefix = Prefix("/"))(createEndpointDef: (Route, Prefix) ⇒ EndpointDefinition): List[EndpointDefinition] = {

    val parserResult = EndpointRoutesParser.parseContent(routeString)

    parserResult.right.map(_.map(createEndpointDef(_, prefix))).right.get

  }

  def endpointOf(endpointDefinition: EndpointDefinition)(
    implicit
    arf: ActorRefFactory,
    ex: ExecutionContext
  ) = {
    val ef = endpointFactory
    ef(endpointDefinition)
  }

  def endpointFactory(
    implicit
    arf: ActorRefFactory,
    ex: ExecutionContext
  ) = {
    implicit val in = new DisabledInterpreter
    EndpointFactory[Nothing](HandlerBridgeProps.default)
  }

  def pathOf(pathParts: List[String] = List("abc", "ep1")): Seq[PathPart] = pathParts.map(StaticPathPart)
}
