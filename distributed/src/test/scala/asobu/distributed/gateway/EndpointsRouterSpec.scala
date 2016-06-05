package asobu.distributed.gateway

import akka.testkit.TestProbe
import asobu.distributed.service.Action.DistributedRequest
import asobu.distributed.util.SpecWithActorCluster
import asobu.distributed.{EndpointDefinition, NullaryEndpointDefinition}
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.service.EndpointDefinitionParser
import play.api.test.FakeRequest
import play.routes.compiler._
import shapeless.HNil
import concurrent.duration._
import asobu.distributed.util.implicits._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

class EndpointsRouterSpec extends SpecWithActorCluster {

  import play.api.http.HttpVerbs._
  val routeString =
    """
      |# Some Comments
      |# Some other Comments
      |GET   /ep1/a   a.b.c()
      |
      |GET   /ep2/b   a.b.d()
      |
    """.stripMargin

  val worker1 = TestProbe()
  val worker2 = TestProbe()
  val createEndpointDef = (route: Route, prefix: Prefix) ⇒ {
    val path = if (route.path.toString.contains("ep1")) worker1.ref.path else worker2.ref.path
    NullaryEndpointDefinition(prefix, route, path, role): EndpointDefinition
  }

  val parserResult = EndpointDefinitionParser.parse(Prefix("/"), routeString, createEndpointDef)

  val endpoints = parserResult.right.get.map(Endpoint(_))

  val router = EndpointsRouter()
  Await.result(router.update(endpoints), 3.seconds)

  "route to worker1" >> {
    router.handle(FakeRequest(GET, "/ep1/a"))
    worker1.expectMsgType[DistributedRequest[HNil]].extracted === HNil
  }

  "route to worker2" >> {
    router.handle(FakeRequest(GET, "/ep2/b"))
    worker2.expectMsgType[DistributedRequest[HNil]].extracted === HNil
  }

}

