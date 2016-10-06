package asobu.distributed.gateway

import akka.actor._
import asobu.distributed.gateway.enricher.DisabledInterpreter
import asobu.distributed.util.{EndpointUtil, TestClusterActorSystem}
import asobu.distributed.{EndpointDefSimple, util, EndpointDefinition}
import asobu.distributed.gateway.Endpoint.{EndpointFactory, Prefix}
import asobu.distributed.service.EndpointRoutesParser
import org.specs2.mock.Mockito
import play.api.mvc.RequestHeader
import play.api.test.{FakeRequest, PlaySpecification}
import play.core.routing.RouteParams

import play.routes.compiler._
import shapeless.HNil
import scala.util.Random

object EndpointSpec extends PlaySpecification with Mockito {
  sequential
  import scala.concurrent.ExecutionContext.Implicits.global
  import play.api.http.HttpVerbs._

  val routeString =
    """
      |# Some Comments
      |# Some other Comments
      |GET   /ep1/a/:n   a.b.c(n: Int)
      |
      |GET   /ep2/:a/something   a.b.d(a: Int, b: String)
      |
    """.stripMargin

  lazy val parserResult = EndpointRoutesParser.parseContent(routeString)
  lazy val routesResult = parserResult.right.get
  lazy val endPoints = EndpointUtil.parseEndpoints(routeString)((route: Route, prefix: Prefix) ⇒ {
    EndpointDefinition(
      prefix,
      route,
      ActorPath.fromString("akka://my-sys/user/service-a/worker1"),
      "role"
    ): EndpointDefinition
  })
  lazy val ep1: EndpointDefinition = endPoints(0)
  lazy val ep2: EndpointDefinition = endPoints(1)

  "EndpointUtil parse to endpoints" >> {
    parserResult must beRight[List[Route]]
    parserResult.right.get.length === 2

    "parse comments" >> {
      routesResult.head.verb === HttpVerb(GET)
      routesResult.head.comments === List(Comment(" Some Comments"), Comment(" Some other Comments"))
    }

    "parse params" >> {
      val route: Route = routesResult.head
      val parameter = route.call.parameters.get.head
      parameter.name === "n"
      parameter.typeName === "Int"
    }

  }

  "unapply" >> {
    def extractParams(epd: EndpointDefinition, request: RequestHeader): Option[RouteParams] = {
      implicit val system = TestClusterActorSystem.create(Random.nextInt(21444) + 2560)()

      val endpoint = EndpointUtil.endpointOf(epd)
      val result = request match {
        case endpoint(params) ⇒ Some(params)
        case _                ⇒ None
      }
      system.terminate()
      result
    }

    "finds pathParameters" >> {
      extractParams(ep1, FakeRequest(GET, "/ep1/a/3")) must
        beSome(RouteParams(Map("n" → Right("3")), Map()))
    }

    "finds queryParameters" >> {
      extractParams(ep2, FakeRequest(GET, "/ep2/3/something?b=foo")) must
        beSome(RouteParams(Map("a" → Right("3")), Map("b" → Seq("foo"))))
    }

    "does not find when method does not match " >> {
      extractParams(ep2, FakeRequest(POST, "/ep2/3/something?b=foo")) must beNone
    }

    "does not find when path does not match " >> {
      extractParams(ep2, FakeRequest(GET, "/unknownendpoint")) must beNone
    }

  }

}

