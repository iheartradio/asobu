package asobu.distributed.service

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import asobu.distributed.EndpointsRegistryUpdater.Add
import asobu.distributed._
import asobu.distributed.gateway.Endpoint.Prefix

import scala.concurrent.Future

trait EndpointsRegistryClient {
  def add(endpointDefinition: EndpointDefinition): Future[Unit]

  /**
   * Used for versionning Endpoints, endpointRegistry won't replace newly received endpoints with the same id and same build number
   * The recommended way is to use sbt-buildInfo to generate a BuildNumber (see [[BuildNumber]]
   */
  def buildNumber: Option[BuildNumber]
}

case class EndpointsRegistryClientImp(
    registry: EndpointsRegistry,
    buildNumber: Option[BuildNumber] = None
)(
    implicit
    system: ActorSystem,
    ao: Timeout
) extends EndpointsRegistryClient {
  import system.dispatcher

  val clientActor = system.actorOf(EndpointsRegistryUpdater.props(registry), "asobu-endpoint-registry-client")

  def add(endpointDefinition: EndpointDefinition): Future[Unit] =
    (clientActor ? Add(endpointDefinition)).map(_ ⇒ ())

}

/**
 * This should be provided by the sbt-buildinfo plugin
 * https://github.com/sbt/sbt-buildinfo
 * Need to add the following two settings at least
 * {{{
 *     buildInfoKeys += buildInfoBuildNumber
 *
 *     buildInfoOptions += BuildInfoOption.Traits("asobu.distributed.service.BuildNumber")
 * }}}
 */
trait BuildNumber {
  def buildInfoBuildNumber: Int
}

