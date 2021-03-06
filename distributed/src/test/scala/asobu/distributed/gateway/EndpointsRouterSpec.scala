package asobu.distributed.gateway

import akka.testkit.TestProbe
import asobu.distributed.FakeRequests
import asobu.distributed.protocol.EndpointDefinition
import asobu.distributed.protocol.{DResult, DRequest}
import asobu.distributed.util.{EndpointUtil, SpecWithActorCluster}
import asobu.distributed.protocol.Prefix
import asobu.distributed.service.EndpointRoutesParser
import play.api.test.FakeRequest
import play.routes.compiler._
import shapeless.HNil
import concurrent.duration._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import EndpointDefinition._

class EndpointsRouterSpec extends SpecWithActorCluster with FakeRequests {

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
    EndpointDefinition(prefix, route, path.handlerAddress, role, None, None)
  }

  val endpointDefs = EndpointUtil.parseEndpoints(routeString)(createEndpointDef)

  val endpoints = endpointDefs.map(EndpointUtil.endpointOf)

  val router = EndpointsRouter()
  Await.result(router.update(endpoints), 3.seconds)

  "route to worker1" >> {
    router.handle(request(GET, "/ep1/a"))
    val rp = worker1.expectMsgType[DRequest].requestParams
    rp.pathParams should beEmpty
  }

  "route to worker2" >> {
    router.handle(request(GET, "/ep2/b"))
    val rp = worker2.expectMsgType[DRequest].requestParams
    rp.queryString should beEmpty
  }

}

