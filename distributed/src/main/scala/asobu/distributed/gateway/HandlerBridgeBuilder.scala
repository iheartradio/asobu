package asobu.distributed.gateway

import javax.inject.Singleton

import akka.actor.Props
import asobu.distributed.gateway.HandlerBridgeProps.{Role, ActorPathString}

trait HandlerBridgeProps extends ((ActorPathString, Role) ⇒ Props)

object HandlerBridgeProps {
  type ActorPathString = String
  type Role = String

  val default = new DefaultHandlerBridgeProps
}

class DefaultHandlerBridgeProps extends HandlerBridgeProps {
  def apply(path: ActorPathString, role: Role): Props =
    ClusterRouters.adaptive(path, role)
}
