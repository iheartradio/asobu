package asobu.distributed.gateway

import akka.actor.{ActorRef, ActorSystem}
import asobu.distributed.{EndpointDefinition, EndpointDefImpl}
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.service.RemoteExtractorDef
import asobu.distributed.util.{SpecWithActorCluster, MockRoute}
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import akka.actor.ActorDSL._

class EndpointsRouterUpdaterSpec extends SpecWithActorCluster {

  "sortOutEndpoints" >> {
    import EndpointsRouterUpdater.sortOutEndpoints
    def mockEndpoingDef(
      version: Option[Int],
      verb: String = "GET",
      pathParts: List[String] = Nil
    ): EndpointDefinition = {

      val handler: ActorRef = actor(new Act {
        become { case _ ⇒ }
      })
      EndpointDefImpl(Prefix("/"), MockRoute(verb = verb, pathParts = pathParts), null, handler.path, "test", version)
    }

    "Keep endpoints that remains the same version" >> {
      val e1 = Endpoint(mockEndpoingDef(Some(1), pathParts = List("abc", "def")))
      val e2 = Endpoint(mockEndpoingDef(Some(3), pathParts = List("qpbg")))

      val existing = List(e1, e2)
      val toUpdate = List(
        mockEndpoingDef(Some(1), pathParts = List("abc", "def")),
        mockEndpoingDef(Some(3), pathParts = List("qpbg"))
      )

      val r = sortOutEndpoints(existing, toUpdate)

      r.toPurge must beEmpty
      r.toKeep must contain(exactly(existing: _*))
      r.toAdd must beEmpty

    }

    "update endpoints that with different version" >> {
      val e1 = Endpoint(mockEndpoingDef(Some(1), pathParts = List("abc", "def")))
      val e2 = Endpoint(mockEndpoingDef(Some(3), pathParts = List("qpbg")))

      val existing = List(e1, e2)
      val newEndpointDef = mockEndpoingDef(Some(3), pathParts = List("abc", "def"))

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
      val e1 = Endpoint(mockEndpoingDef(None, pathParts = List("abc", "def")))
      val e2 = Endpoint(mockEndpoingDef(Some(3), pathParts = List("qpbg")))

      val existing = List(e1, e2)
      val newEndpointDef = mockEndpoingDef(Some(3), pathParts = List("abc", "def"))
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
      val e1 = Endpoint(mockEndpoingDef(Some(3), pathParts = List("abc", "def")))
      val e2 = Endpoint(mockEndpoingDef(Some(3), pathParts = List("qpbg")))

      val existing = List(e1, e2)
      val newEndpointDef = mockEndpoingDef(None, pathParts = List("abc", "def"))
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
      val e1 = Endpoint(mockEndpoingDef(Some(3), pathParts = List("abc", "def")))
      val e2 = Endpoint(mockEndpoingDef(Some(1), pathParts = List("different")))

      val newEf2 = mockEndpoingDef(Some(1), pathParts = List("qpbg"))

      val existing = List(e1, e2)
      val toUpdate = List(
        mockEndpoingDef(Some(3), pathParts = List("abc", "def")),
        newEf2
      )

      val r = sortOutEndpoints(existing, toUpdate)

      r.toPurge === List(e2)
      r.toKeep === List(e1)
      r.toAdd === List(newEf2)

    }

    "add all new endpoints" >> {

      val newEf1 = mockEndpoingDef(Some(3), pathParts = List("abc", "def"))
      val newEf2 = mockEndpoingDef(Some(1), pathParts = List("qpbg"))

      val r = sortOutEndpoints(Nil, List(newEf1, newEf2))

      r.toPurge must beEmpty
      r.toKeep must beEmpty
      r.toAdd must contain(exactly(newEf1, newEf2))

    }

  }

}
