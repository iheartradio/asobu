package asobu.distributed.gateway

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberLeft, MemberExited, MemberRemoved, UnreachableMember}
import akka.cluster.singleton._

import asobu.distributed.{EndpointsRegistryUpdater, EndpointsRegistry}
import asobu.distributed.EndpointsRegistryUpdater.Sanitize
import concurrent.duration._

class ClusterHealthCheck(registry: EndpointsRegistry) extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  val updater = context.actorOf(EndpointsRegistryUpdater.props(registry))

  cluster.subscribe(self, classOf[MemberLeft], classOf[MemberRemoved], classOf[MemberExited])

  def receive: Receive = {
    case e @ (_: MemberRemoved | _: MemberExited | _: MemberLeft) ⇒
      log.info(s"Cluster Member change noticed $e, sanitizing")
      updater ! Sanitize
  }
}

object ClusterHealthCheck {

  def props(registry: EndpointsRegistry): Props = {
    val checkerProps = Props(new ClusterHealthCheck(registry))
    ClusterSingletonManager.props(
      singletonProps     = checkerProps,
      terminationMessage = PoisonPill,
      settings           = new ClusterSingletonManagerSettings("EndpointRegistryClusterHealthChecker", None, 3.seconds, 3.seconds)
    )
  }

}

