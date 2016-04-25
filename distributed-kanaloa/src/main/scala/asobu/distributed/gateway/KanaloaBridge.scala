package asobu.distributed.gateway

import akka.actor.{Props, ActorSystem}
import asobu.distributed.gateway.HandlerBridgeProps.{Role, ActorPathString}
import kanaloa.reactive.dispatcher.Backend.BackendAdaptor
import kanaloa.reactive.dispatcher.{ResultChecker, Backend, PushingDispatcher}
import play.api.Configuration

abstract class AbstractKanaloaBridge(implicit config: Configuration, system: ActorSystem)
    extends HandlerBridgeProps {
  protected def resultChecker: ResultChecker
  def apply(path: ActorPathString, role: Role): Props = {
    val router = system.actorOf(ClusterRouters.adaptive(path, role)) //router should be kept alive, and thus we cannot just use the Prop here.
    val dispatcherName: String = path.replace("/user/", "").replace("/", "__").replace(".", "_")
    PushingDispatcher.props(
      dispatcherName, router, config.underlying
    )(resultChecker)
  }

}
