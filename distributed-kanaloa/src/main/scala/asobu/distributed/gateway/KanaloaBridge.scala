package asobu.distributed.gateway

import akka.actor.{Props, ActorSystem}
import akka.util.Timeout
import asobu.distributed.gateway.HandlerBridgeProps.{Role, ActorPathString}
import kanaloa.reactive.dispatcher.{ClusterAwareBackend, ResultChecker, PushingDispatcher}
import play.api.Configuration

abstract class AbstractKanaloaBridge(implicit config: Configuration, system: ActorSystem, timeout: Timeout)
    extends HandlerBridgeProps {
  protected def resultChecker: ResultChecker
  def apply(path: ActorPathString, role: Role): Props = {
    val dispatcherName: String = path.replace("/user/", "").replace("/", "__").replace(".", "_")
    PushingDispatcher.props(
      dispatcherName, ClusterAwareBackend(path, role), config.underlying
    )(resultChecker)
  }

}
