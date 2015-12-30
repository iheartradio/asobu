package util

import javax.inject.Inject

import akka.actor.{ActorSystem, Props}
import api.ErrorResult
import asobu.distributed.gateway.{ClusterRouters, HandlerBridgeProps}
import asobu.distributed.gateway.HandlerBridgeProps.{Role, ActorPathString}
import kanaloa.reactive.dispatcher.PushingDispatcher
import play.api.Configuration

class KanaloaBridge @Inject() (implicit config: Configuration, system: ActorSystem) extends HandlerBridgeProps {
  def apply(path: ActorPathString, role: Role): Props = {
    val router = system.actorOf(ClusterRouters.adaptive(path, role)) //router should be kept alive, and thus we cannot just use the Prop here.
    val dispatcherName: String = path.replace("/user/", "").replace("/", "__").replace(".", "_")
    PushingDispatcher.props(
      dispatcherName, router, config.underlying
    ) {
      case e: ErrorResult[_] ⇒ Left(e.toString)
      case m                ⇒ Right(m)
    }
  }

}
