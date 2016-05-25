package asobu.distributed.gateway

import javax.inject.{Provider, Inject, Singleton}

import akka.actor.{Deploy, ActorRef, ActorSystem, Props}

import akka.routing.{SmallestMailboxPool, DefaultOptimalSizeExploringResizer, RoundRobinPool}
import asobu.distributed.{SystemValidator, DefaultEndpointsRegistry, EndpointDefinition, EndpointsRegistry}
import play.api.{Configuration, Environment}
import play.api.inject.Module

@Singleton
class Gateway @Inject() (implicit system: ActorSystem, handlerBridgeProps: HandlerBridgeProps) {

  val validatorResult = SystemValidator.validate
  assert(validatorResult.isRight, validatorResult.left.get)

  val entryActor: ActorRef = {
    val routerProps =
      SmallestMailboxPool(
        8,
        Some(DefaultOptimalSizeExploringResizer(upperBound = 200))
      ).props(EndpointsRouter.props)
    system.actorOf(routerProps, "asobu-gateway-router")
  }

  private val registry: DefaultEndpointsRegistry = DefaultEndpointsRegistry()

  system.actorOf(
    EndpointsRouterUpdater.props(
      registry,
      entryActor,
      handlerBridgeProps
    ), "asobu-gateway-routers-updater"
  )

  system.actorOf(ClusterHealthCheck.props(registry), "asobu-cluster-health-monitor")

  lazy val apiDocsRegistry =
    system.actorOf(ApiDocumentationRegistry.props(registry), "asobu-api-documentation-registry")

}

/**
 * Eagerly start the Gateway
 */
class GateWayModule extends Module {
  protected def defaultBridgeClass: Class[_ <: HandlerBridgeProps] = classOf[DefaultHandlerBridgeProps]

  final def bindings(
    environment: Environment,
    configuration: Configuration
  ) = {
    val bridgeClass: Class[_ <: HandlerBridgeProps] = {
      val bridgePropsClassName = configuration.getString("asobu.bridgePropsClass")
      bridgePropsClassName.map(n ⇒ environment.classLoader.loadClass(n).asSubclass(classOf[HandlerBridgeProps]))
    }.getOrElse(defaultBridgeClass)

    Seq(
      bind[Gateway].toSelf.eagerly,
      bind[HandlerBridgeProps].to(bridgeClass)
    )
  }
}
