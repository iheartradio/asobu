package asobu.distributed.gateway

import akka.actor.{Props, ActorSystem, ActorRef}
import akka.cluster.Cluster
import akka.cluster.metrics.AdaptiveLoadBalancingGroup
import akka.cluster.routing.{ClusterRouterGroupSettings, ClusterRouterGroup}
import akka.routing.{Group, RoundRobinGroup, FromConfig}

import scala.concurrent.{Future, Promise}

/**
 * AkkaCluster enabled Akka routers todo: this would not be needed with the new kanaloa backend.
 */
object ClusterRouters {

  def roundRobin(routeePath: String, role: String): Props =
    clusterRouterGroup(
      RoundRobinGroup(List(routeePath)),
      routeePath,
      role
    )

  def adaptive(routeePath: String, role: String): Props =
    clusterRouterGroup(
      AdaptiveLoadBalancingGroup(paths = List(routeePath)),
      routeePath,
      role
    )

  def clusterRouterGroup(localGroup: Group, routeePath: String, role: String): Props =
    ClusterRouterGroup(
      localGroup,
      ClusterRouterGroupSettings(
        totalInstances    = 100,
        routeesPaths      = List(routeePath),
        allowLocalRoutees = true, useRole = Some(role)
      )
    ).props()

}
