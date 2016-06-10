package asobu.distributed.gateway

import java.io.InvalidClassException

import akka.actor.{ActorRef, ExtendedActorSystem, UnhandledMessage}
import asobu.distributed._
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.util.{MockRoute, SpecWithActorCluster}
import akka.actor.ActorDSL._
import akka.cluster.Cluster
import akka.cluster.ddata.LWWMap
import akka.cluster.ddata.Replicator.{Changed, GetSuccess}
import akka.serialization.{JavaSerializer, Serializer}
import akka.testkit.TestProbe
import util.implicits._

class EndpointsRouterUpdaterSpec extends SpecWithActorCluster {
  import scala.concurrent.ExecutionContext.Implicits.global

  def mockEndpointDef(
    version: Option[Int],
    verb: String = "GET",
    pathParts: List[String] = Nil
  ): EndpointDefinition = {
    val handler: ActorRef = actor(new Act {
      become { case _ ⇒ }
    })
    EndpointDefImpl(Prefix("/"), MockRoute(verb = verb, pathParts = pathParts), null, handler.path, "test", version)
  }

  "sortOutEndpoints" >> {
    import EndpointsRouterUpdater.sortOutEndpoints

    "Keep endpoints that remains the same version" >> {
      val e1 = Endpoint(mockEndpointDef(Some(1), pathParts = List("abc", "def")))
      val e2 = Endpoint(mockEndpointDef(Some(3), pathParts = List("qpbg")))

      val existing = List(e1, e2)
      val toUpdate = List(
        mockEndpointDef(Some(1), pathParts = List("abc", "def")),
        mockEndpointDef(Some(3), pathParts = List("qpbg"))
      )

      val r = sortOutEndpoints(existing, toUpdate)

      r.toPurge must beEmpty
      r.toKeep must contain(exactly(existing: _*))
      r.toAdd must beEmpty

    }

    "update endpoints that with different version" >> {
      val e1 = Endpoint(mockEndpointDef(Some(1), pathParts = List("abc", "def")))
      val e2 = Endpoint(mockEndpointDef(Some(3), pathParts = List("qpbg")))

      val existing = List(e1, e2)
      val newEndpointDef = mockEndpointDef(Some(3), pathParts = List("abc", "def"))

      val toUpdate = List(
        newEndpointDef,
        e2.definition
      )

      val r = sortOutEndpoints(existing, toUpdate)

      r.toPurge === List(e1)
      r.toKeep === List(e2)
      r.toAdd === List(newEndpointDef)

    }

    "update endpoint without old version" >> {
      val e1 = Endpoint(mockEndpointDef(None, pathParts = List("abc", "def")))
      val e2 = Endpoint(mockEndpointDef(Some(3), pathParts = List("qpbg")))

      val existing = List(e1, e2)
      val newEndpointDef = mockEndpointDef(Some(3), pathParts = List("abc", "def"))
      val toUpdate = List(
        newEndpointDef,
        e2.definition
      )

      val r = sortOutEndpoints(existing, toUpdate)

      r.toPurge === List(e1)
      r.toKeep === List(e2)
      r.toAdd === List(newEndpointDef)

    }

    "update endpoint without new version" >> {
      val e1 = Endpoint(mockEndpointDef(Some(3), pathParts = List("abc", "def")))
      val e2 = Endpoint(mockEndpointDef(Some(3), pathParts = List("qpbg")))

      val existing = List(e1, e2)
      val newEndpointDef = mockEndpointDef(None, pathParts = List("abc", "def"))
      val toUpdate = List(
        newEndpointDef,
        e2.definition
      )

      val r = sortOutEndpoints(existing, toUpdate)

      r.toPurge === List(e1)
      r.toKeep === List(e2)
      r.toAdd === List(newEndpointDef)

    }

    "update endpoints that with different path" >> {
      val e1 = Endpoint(mockEndpointDef(Some(3), pathParts = List("abc", "def")))
      val e2 = Endpoint(mockEndpointDef(Some(1), pathParts = List("different")))

      val newEf2 = mockEndpointDef(Some(1), pathParts = List("qpbg"))

      val existing = List(e1, e2)
      val toUpdate = List(
        mockEndpointDef(Some(3), pathParts = List("abc", "def")),
        newEf2
      )

      val r = sortOutEndpoints(existing, toUpdate)

      r.toPurge === List(e2)
      r.toKeep === List(e1)
      r.toAdd === List(newEf2)

    }

    "add all new endpoints" >> {

      val newEf1 = mockEndpointDef(Some(3), pathParts = List("abc", "def"))
      val newEf2 = mockEndpointDef(Some(1), pathParts = List("qpbg"))

      val r = sortOutEndpoints(Nil, List(newEf1, newEf2))

      r.toPurge must beEmpty
      r.toKeep must beEmpty
      r.toAdd must contain(exactly(newEf1, newEf2))

    }

  }

  "ignore unknown message types" >> {
    val listener = TestProbe()
    system.eventStream.subscribe(listener.ref, classOf[UnhandledMessage])

    val registry = new DefaultEndpointsRegistry(system)
    val updater = system.actorOf(EndpointsRouterUpdater.props(
      registry,
      new EndpointsRouter()
    ))

    implicit val cluster = Cluster(system)

    val data = (LWWMap.empty[String] + "dummy-key" → "dummy-value")
      .asInstanceOf[LWWMap[EndpointDefinition]]

    def check[A](message: A) = {
      val test = TestProbe()
      test.send(updater, message)
      val unhandled = UnhandledMessage(message, test.ref, updater)
      listener.expectMsg(unhandled) === unhandled
    }

    check(GetSuccess(registry.EndpointsDataKey, None)(data))
    check(Changed(registry.EndpointsDataKey)(data))
  }

}

