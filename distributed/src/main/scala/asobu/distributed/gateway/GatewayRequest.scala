package asobu.distributed.gateway

import play.api.mvc.{AnyContent, Request}
import play.core.routing.RouteParams

case class GateWayRequest(routeParam: RouteParams, request: Request[AnyContent])
