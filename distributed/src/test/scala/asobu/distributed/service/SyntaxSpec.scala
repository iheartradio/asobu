package asobu.distributed.service

import akka.actor.{Actor, Props}
import akka.actor.Actor.Receive
import akka.stream.ActorMaterializer
import akka.util.Timeout
import asobu.distributed.FakeRequests
import asobu.distributed.protocol.Prefix
import asobu.distributed.protocol.EndpointDefinition
import asobu.distributed.service.DRequestExtractorSpec._
import asobu.distributed.protocol.{DRequest, RequestParams}
import asobu.distributed.util.{MockRoute, ScopeWithActor, SerializableTest, SpecWithActorCluster}

import asobu.dsl.extractors.JsonBodyExtractor
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.mutable.ExecutionEnvironment
import play.api.libs.json.{JsNumber, JsString, Json}
import play.api.mvc.Results.Ok
import play.core.routing.RouteParams
import shapeless.record.Record

import scala.concurrent.Future
import play.api.test.FakeRequest
import cats.instances.future._
import concurrent.duration._

class SyntaxSpec extends SpecWithActorCluster with FakeRequests with SerializableTest with ExecutionEnvironment {
  import asobu.distributed.service.SyntaxSpec._
  implicit val format = Json.format[Input]
  import asobu.dsl.DefaultExtractorImplicits._
  implicit val erc: EndpointsRegistryClient = new EndpointsRegistryClient {
    def add(endpointDefinition: EndpointDefinition): Future[Unit] = Future.successful(())

    def prefix: Prefix = Prefix("/")

    def buildNumber: Option[BuildNumber] = None
  }

  implicit val ao: Timeout = 1.seconds
  val testBE = system.actorOf(testBackend)
  implicit val mat = ActorMaterializer()

  def is(implicit ee: ExecutionEnv) = {
    "can build endpoint extracting params only" >> new SyntaxScope {
      val endpoint = endpointOf(handle(
        "anEndpoint",
        process[Input]()
      )(using(testBE).expect[Output] >> respond(Ok)))

      endpoint must beSerializable
    }

    "can build endpoint extracting params and body only" >> new SyntaxScope {
      val endpoint = endpointOf(handle(
        "anEndpoint",
        process[Input](fromJsonBody[Input])
      )(using(testBE).expect[Output] >> respond(Ok)))

      endpoint must beSerializable
    }

    "can build endpoint extracting params and body as nested field" >> new SyntaxScope {
      val endpoint = endpointOf(handle(
        "anEndpoint",
        process[NestedInput](from(child = jsonBody[Input]))
      )(using(testBE).expect[Output] >> respond(Ok)))

      endpoint must beSerializable
    }

    "can build endpoint extracting param body, and header" >> new SyntaxScope {
      import asobu.distributed.service.extractors.DRequestExtractors._
      val action = handle(
        "anEndpoint",
        process[LargeInput](
          from(flagInHeader = header[Boolean]("someheaderField")) and
            fromJsonBody[Input]
        )
      )(using(testBE).expect[Output] >> respond(Ok))

      val endpoint = endpointOf(action)

      endpoint must beSerializable

      val params = RequestParams(Map.empty, Map.empty)
      val req = request().withHeaders("someheaderField" → "true").withBody(rawJson(Json.obj("a" → JsString("avalue"), "b" → JsNumber(10))))

      val dRequest = DRequest(params, req)

      val localResult = action.extractor.run(dRequest).toEither

      localResult must beRight(LargeInput(a = "avalue", b = 10, flagInHeader = true)).await(retries = 0, timeout = 3.seconds)

    }
  }

}

trait SyntaxScope extends ScopeWithActor with Controller with Syntax {
  import play.api.http.HttpVerbs._

  def endpointOf(action: Action): EndpointDefinition = {
    val route = MockRoute(handlerClass = action.getClass.getName, pathParts = Nil)
    action.endpointDefinition(route, Prefix.root, None)
  }

  def actions: List[Action] = Nil

}

object SyntaxSpec {
  class TestBackend extends Actor {
    def receive: Receive = {
      case Input(a, b) ⇒ sender ! Output(a + b)
    }
  }

  def testBackend: Props = Props(new TestBackend)
  case class LargeInput(a: String, b: Int, flagInHeader: Boolean)
  case class Input(a: String, b: Int)
  case class NestedInput(child: Input, c: Boolean)
  case class Output(a: String)
}
