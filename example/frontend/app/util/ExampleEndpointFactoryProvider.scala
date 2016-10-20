package util

import javax.inject.{Singleton, Provider, Inject}

import akka.actor.{ActorSystem, ActorRefFactory}
import api.ExampleEnricher
import asobu.distributed.gateway.{EndpointFactoryProvider, DefaultHandlerBridgeProps}
import asobu.distributed.gateway.Endpoint.EndpointFactory
import asobu.distributed.gateway.enricher.DisabledInterpreter
import cross.ExampleInterpreter

import scala.concurrent.ExecutionContext

@Singleton
class ExampleEndpointFactoryProvider @Inject() (
                                                 implicit
                                                 system: ActorSystem,
                                                 ec: ExecutionContext,
                                                 bridge: KanaloaBridge
                                               ) extends EndpointFactoryProvider {
  def apply() = {
    implicit val interpreter = new ExampleInterpreter
    EndpointFactory[ExampleEnricher](bridge)
  }
}

