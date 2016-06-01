package asobu.distributed

import _root_.akka.actor.{ActorSystem, ActorRef}
import _root_.akka.util.Timeout
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.gateway.{GatewayRouter, DefaultHandlerBridgeProps, Gateway}
import asobu.distributed.service._
import asobu.distributed.util.{TestClusterActorSystem, SpecWithActorCluster}
import asobu.dsl._
import asobu.dsl.extractors.HeaderExtractors
import asobu.dsl.util.Read
import junit.framework.TestResult
import play.api.libs.json.{JsNumber, JsString, Json}
import play.api.mvc.Results._
import concurrent.duration._
import scala.util.{Random, Try}
import play.api.http.HttpVerbs._
import play.api.test.{PlaySpecification, FakeRequest}

class End2EndSpec extends PlaySpecification with SpecWithActorCluster {
  import End2EndSpec._
  val gatewayAction = DistributedSystem.GatewayApp().action
  Thread.sleep(1000)
  (new DistributedSystem.ServiceBackend.App)
  Thread.sleep(1000)

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

    case class GatewayApp(implicit system: ActorSystem) {
      implicit val gw = new Gateway()(system, new DefaultHandlerBridgeProps)
      import system.dispatcher
      val action = new GatewayRouter().handleAll
    }

    object ServiceBackend {
      import _root_.akka.actor.ActorDSL._

      def testServiceBackendRef(implicit system: ActorSystem) = actor(new Act {
        import Domain._
        become {
          case CatRequest(id)           ⇒ sender() ! Cat(id, Random.nextInt(20))
          case DogRequest(_, owner, by) ⇒ sender() ! Dog("big", 2020 - by, "golden", owner)
        }
      })

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
        import concurrent.ExecutionContext.Implicits.global
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

      case class App(implicit system: ActorSystem) {
        init(Prefix("/api"))(
          TestController(testServiceBackendRef)
        )
      }
    }

  }

}
