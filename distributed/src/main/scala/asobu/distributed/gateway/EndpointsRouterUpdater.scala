package asobu.distributed.gateway

import akka.actor._
import akka.cluster.ddata.LWWMap
import akka.cluster.ddata.Replicator._
import asobu.distributed.CustomRequestExtractorDefinition.Interpreter
import asobu.distributed.gateway.EndpointsRouterUpdater.InvalidDData
import asobu.distributed.{EndpointDefinition, EndpointsRegistry}

import scala.concurrent.duration._
import cats.std.option._
import cats.syntax.cartesian._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
 * The endpoint manager at the gateway side that monitors
 * the registry and update the http request router
 *
 * @param registry
 * @param ec This is the execution context that all requests will be run on
 */
class EndpointsRouterUpdater(
    registry: EndpointsRegistry,
    endpointsRouter: EndpointsRouter,
    bridgeProps: HandlerBridgeProps,
    readTimeout: FiniteDuration = 30.seconds
)(implicit ec: ExecutionContext, interpreter: Interpreter) extends Actor with ActorLogging {
  import registry._
  import EndpointsRouterUpdater.{sortOutEndpoints, SortResult}
  replicator ! Subscribe(EndpointsDataKey, self)
  replicator ! Get(EndpointsDataKey, ReadMajority(readTimeout))

  def receive = monitoring(Nil)

  def monitoring(endpoints: List[Endpoint]): Receive = {
    def updateEndpoints(data: LWWMap[EndpointDefinition]): Unit = {
      val newDefs: List[EndpointDefinition] = data.entries.values.toList

      val SortResult(toAdd, toPurge, toKeep) = sortOutEndpoints(endpoints, newDefs)

      if (toPurge.nonEmpty || toAdd.nonEmpty) {
        val updatedEndpoints = toKeep ++ toAdd.map(Endpoint(_, bridgeProps))

        endpointsRouter.update(updatedEndpoints)

        //TODO: only purge before all endpoints are updated at all endpointsRouters, this could be challenging because the number of endpointRouters are dynamic.
        toPurge.foreach(_.shutdown())

        log.info("Endpoints updated, removed: " + toPurge.map(_.definition.id).mkString(" ") + " added: " + toAdd.map(_.id).mkString(" "))
        context become monitoring(updatedEndpoints)
      }

    }

    /**
     * This is a workaround for handling issues with erasure and akka's serialization. Serializers can change the type
     * of a message (whether inadvertently or not), and that value can be a parameterized value. This message will be
     * send on and only later result in a ClassCastException. When this happens, the actor dies and is restarted,
     * the update is retried, and the process is consumed in a retry loop. This allows one invalid message to disrupt
     * all communication. This workaround tests a single value as a simple heuristic to avoid killing the actor,
     * and simply logs and drops the update. This sacrifices consistency for increased resiliency.
     */
    def update[A](data: A)(extract: A ⇒ LWWMap[EndpointDefinition]): Unit = {
      val results = extract(data)
      if (!results.entries.headOption.forall(_._2.isInstanceOf[EndpointDefinition])) {
        log.error(s"Received ddata message with invalid underlying data")
        unhandled(data)
      } else {
        updateEndpoints(results)
      }
    }

    {
      case c @ Changed(EndpointsDataKey) ⇒
        update(c)(_.get(EndpointsDataKey))

      case g @ GetSuccess(EndpointsDataKey, _) ⇒
        log.info("Endpoints initialized")
        update(g)(_.get(EndpointsDataKey))

      case GetFailure(EndpointsDataKey, _) ⇒
        log.error("Failed to get endpoint registry")

      case NotFound(EndpointsDataKey, _) ⇒
        log.info("No data in endpoint registry yet.")

    }
  }

}

object EndpointsRouterUpdater {
  case class InvalidDData[T](data: T, reason: String)

  def props(
    registry: EndpointsRegistry,
    endpointsRouter: EndpointsRouter,
    bridgeProps: HandlerBridgeProps = HandlerBridgeProps.default
  )(implicit ec: ExecutionContext, interpreter: Interpreter) =
    Props(new EndpointsRouterUpdater(registry, endpointsRouter, bridgeProps))

  private[gateway] def sortOutEndpoints(existing: List[Endpoint], toUpdate: List[EndpointDefinition]): SortResult = {
    val (toKeep, toPurge) = existing.partition { ep ⇒
      toUpdate.exists { newDef ⇒
        newDef.id == ep.definition.id &&
          ((ep.definition.version |@| newDef.version) map (_ == _)).getOrElse(false) //replace when version is different or missing
      }
    }

    val toAdd = toUpdate.filterNot { newDef ⇒
      toKeep.exists(_.definition.id == newDef.id)
    }

    SortResult(toAdd = toAdd, toPurge = toPurge, toKeep = toKeep)
  }

  case class SortResult(toAdd: List[EndpointDefinition], toPurge: List[Endpoint], toKeep: List[Endpoint])
}
