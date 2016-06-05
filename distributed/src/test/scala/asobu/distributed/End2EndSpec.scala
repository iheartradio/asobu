package asobu.distributed

import asobu.distributed.CustomRequestExtractorDefinition.Interpreter
import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.util.Timeout
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.gateway.{DefaultHandlerBridgeProps, EndpointsRouter, Gateway, GatewayRouter}
import asobu.distributed.service._
import asobu.distributed.util.SpecWithActorCluster
import asobu.dsl.RequestExtractor
import play.api.libs.json.{JsNumber, JsString, Json}
import play.api.mvc.Results._

import concurrent.duration._
import util.implicits._

import scala.util.Random
import play.api.test.{FakeRequest, PlaySpecification}

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class End2EndSpec extends PlaySpecification with SpecWithActorCluster {

  import End2EndSpec._

  val gateway = DistributedSystem.GatewayApp(system)
  val gatewayAction = gateway.action
  val app = new DistributedSystem.ServiceBackend.App()

  def until[A](value: ⇒ Future[A])(condition: A ⇒ Boolean): Future[A] =
    value.filter(condition).recoverWith {
      case _: Throwable ⇒ until(value)(condition)
    }

  def awaitNonEmptyRoutes(duration: FiniteDuration) =
    Await.result(until(gateway.router.endpointsAgent.future)(_._2.nonEmpty), duration)

  awaitNonEmptyRoutes(5.seconds)

  "GET /api/cats/:catId returns OK Json response" >> {
    val req = FakeRequest(GET, "/api/cats/3")
    val resp = gatewayAction(req)
    status(resp) === OK
    (contentAsJson(resp) \ "id").get === JsString("3")
    (contentAsJson(resp) \ "age").get must beAnInstanceOf[JsNumber]

  }

  "GET /api/dogs/:name?birthYear=2012 returns OK Json response" >> {
    val req = FakeRequest(GET, "/api/dogs/tom?birthYear=2012").withHeaders("owner" → "Brian")
    val resp = gatewayAction(req)
    status(resp) === OK
    (contentAsJson(resp) \ "age").get === JsNumber(2020 - 2012)
    (contentAsJson(resp) \ "breed").get === JsString("golden")
    (contentAsJson(resp) \ "owner").get === JsString("Brian")

  }

  "GET /api/human/:catId returns 404" >> {
    val req = FakeRequest(GET, "/api/human/3")
    val resp = gatewayAction(req)
    status(resp) === NOT_FOUND
  }

}

object End2EndSpec {
  implicit val ao: Timeout = 5.seconds

  object DistributedSystem {

    val interpreter = new Interpreter {
      override def interpret[T](cred: CustomRequestExtractorDefinition[T]): RequestExtractor[T] = ???
    }

    case class GatewayApp(system: ActorSystem) {
      val router = new EndpointsRouter
      val gw = new Gateway(new DefaultHandlerBridgeProps, system, router)(global, interpreter)
      val action = new GatewayRouter(router).handleAll
    }

    object ServiceBackend {
      import _root_.akka.actor.ActorDSL._

      def testServiceBackendRef(system: ActorRefFactory) = {
        implicit val factory = system
        actor(new Act {
          import Domain._
          become {
            case CatRequest(id)           ⇒ sender() ! Cat(id, Random.nextInt(20))
            case DogRequest(_, owner, by) ⇒ sender() ! Dog("big", 2020 - by, "golden", owner)
          }
        })
      }

      object Domain {
        case class CatRequest(catId: String)
        case class Cat(id: String, age: Int)
        case class DogRequest(name: String, owner: String, birthYear: Int)
        case class Dog(id: String, age: Int, breed: String, owner: String)
      }

      //The routes file is in resources/TestController.routes
      case class TestController(serviceBackend: ActorRef) extends DistributedController {
        import Domain._
        implicit val catFormat = Json.format[Cat]
        implicit val dogFormat = Json.format[Dog]
        import asobu.dsl.DefaultExtractorImplicits._

        val actions = List(
          handle(
            "cats",
            process[CatRequest]()
          )(
              using(serviceBackend).
                expect[Cat] >>
                respondJson(Ok)
            ),

          handle(
            "dogs",
            process[DogRequest](
              from(owner = header[String]("owner")) //todo: this should fail
            )
          )(
              using(serviceBackend).
                expect[Dog] >>
                respondJson(Ok)
            )
        )
      }

      case class App()(implicit system: ActorSystem) {
        val started = init(Prefix("/api"))(
          TestController(testServiceBackendRef(system))
        )
      }
    }

  }

}
