package asobu.distributed.gateway

import javax.inject.{Provider, Inject, Singleton}

import akka.actor.{Deploy, ActorRef, ActorSystem, Props}

import akka.routing.{SmallestMailboxPool, DefaultOptimalSizeExploringResizer, RoundRobinPool}
import asobu.distributed.CustomRequestExtractorDefinition.Interpreter
import asobu.distributed._
import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}

import scala.reflect.ClassTag

@Singleton
class Gateway @Inject() (implicit system: ActorSystem, handlerBridgeProps: HandlerBridgeProps, interpreter: Interpreter) {

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
  protected def defaultInterpreterClass: Class[_ <: Interpreter] = classOf[DisabledCustomExtractorInterpreter]

  private class syntax[T: ClassTag](self: Option[Class[_ <: T]]) {
    def withDefault[DT <: T](defaultClass: Class[_ <: T]): Binding[T] =
      bind[T].to(self.getOrElse(defaultClass))
  }

  final def bindings(
    environment: Environment,
    configuration: Configuration
  ) = {

    def bindFromConfig[T: ClassTag](cfgName: String): syntax[T] = {
      val bindClass: Class[T] = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
      val className = configuration.getString(s"asobu.$cfgName")
      new syntax[T](className.map(n ⇒ environment.classLoader.loadClass(n).asSubclass(bindClass)))
    }

    Seq(
      bind[Gateway].toSelf.eagerly,
      bindFromConfig[HandlerBridgeProps]("bridgePropsClass").withDefault(defaultBridgeClass),
      bindFromConfig[Interpreter](CustomRequestExtractorDefinition.interpreterClassConfigKey).withDefault(defaultInterpreterClass)
    )
  }
}
