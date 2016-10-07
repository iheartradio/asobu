package asobu.distributed

import akka.actor.{ActorRef, ActorRefFactory, ActorSystem}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import asobu.distributed.gateway.Endpoint.{EndpointFactory}
import asobu.distributed.protocol.Prefix
import asobu.distributed.gateway.enricher.{Interpreter, DisabledInterpreter}
import asobu.distributed.gateway._
import asobu.distributed.service._
import asobu.distributed.util.SpecWithActorCluster
import asobu.dsl.{ExtractResult, Extractor, RequestExtractor}
import play.api.libs.json.{JsNumber, JsString, Json}
import play.api.mvc.Results._
import asobu.distributed.protocol.{DRequest, DResult}
import concurrent.duration._

import scala.util.Random
import play.api.test.{FakeRequest, PlaySpecification}
import asobu.dsl.CatsInstances._
import scala.concurrent.{ExecutionContext, Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class End2EndSpec extends PlaySpecification with SpecWithActorCluster {

  import End2EndSpec._

  val gateway = DistributedSystem.GatewayApp()
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
  "GET /api/auth-dogs/ without userid in header returns Unauthorized response" >> {
    val req = FakeRequest(GET, "/api/auth-dogs/tom?birthYear=2012").withHeaders("owner" → "Brian")
    val resp = gatewayAction(req)
    status(resp) === UNAUTHORIZED
  }

  "GET /api/perm-dogs/ without userid in header returns Unauthorized response" >> {
    val req = FakeRequest(GET, "/api/perm-dogs/tom?birthYear=2012").withHeaders("owner" → "Brian")
    val resp = gatewayAction(req)
    status(resp) === UNAUTHORIZED
  }

  "GET /api/no-perm-dogs/ with userid in header but not correct permission returns Unauthorized response" >> {
    val req = FakeRequest(GET, "/api/no-perm-dogs/tom?birthYear=2012").withHeaders("owner" → "Brian", "user_token" → "323sdf230@098d23")
    val resp = gatewayAction(req)
    status(resp) === UNAUTHORIZED
  }

  "GET /api/perm-dogs/ with userid in header and correct permission returns OK response" >> {
    val req = FakeRequest(GET, "/api/perm-dogs/tom?birthYear=2012").withHeaders("owner" → "Brian", "user_token" → "323sdf230@098d23")
    val resp = gatewayAction(req)
    status(resp) === OK
  }

  "GET /api/auth-dogs/ with userid in header returns Ok response" >> {
    val req = FakeRequest(GET, "/api/auth-dogs/tom?birthYear=2012").withHeaders("owner" → "Brian", "user_token" → "323sdf230@098d23")
    val resp = gatewayAction(req)
    status(resp) === OK
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
    import Extractor._
    sealed trait ExampleEnricher extends RequestEnricherDefinition

    case object Authenticated extends ExampleEnricher
    case class Authorized(permissionRequired: String) extends ExampleEnricher

    class ExampleInterpreter extends Interpreter[ExampleEnricher] {
      def apply(ee: ExampleEnricher)(implicit exec: ExecutionContext): RequestEnricher = {
        ee match {
          case Authenticated ⇒
            (dr: DRequest) ⇒ {
              val tokenO = dr.headers.toMap.get("user_token")
              tokenO.fold(ExtractResult.left[DRequest](Unauthorized("Cannot find user_token in header")))(
                token ⇒ ExtractResult.pure(dr.copy(headers = dr.headers :+ ("user_id" → "234")))
              )
            }
          case Authorized(permission) ⇒
            (dr: DRequest) ⇒ {
              val userId = dr.headers.toMap.get("user_id")
              userId.fold(ExtractResult.left[DRequest](Unauthorized("Cannot find user_id in header")))(
                userId ⇒ {
                  if (permission != "print")
                    ExtractResult.left[DRequest](Unauthorized(s"User cannot perform $permission"))
                  else
                    ExtractResult.pure(dr)
                }
              )
            }
        }
      }
    }

    implicit val interpreter = new ExampleInterpreter

    case class GatewayApp(implicit system: ActorSystem) {
      val efp = new EndpointFactoryProvider {
        def apply() = EndpointFactory[ExampleEnricher](HandlerBridgeProps.default)
      }
      val router = new EndpointsRouter
      val gw = new Gateway(efp, system, router)
      val action = new GatewayRouter(router).handleAll
    }

    object ServiceBackend {
      import _root_.akka.actor.ActorDSL._
      import extractors.DRequestExtractors._

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
      case class TestController(serviceBackend: ActorRef)(implicit mat: ActorMaterializer) extends DistributedController {
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
              from(owner = header[String]("owner"))
            )
          )(
              using(serviceBackend).
                expect[Dog] >>
                respondJson(Ok)
            ),

          handle(
            "authDogs",
            Authenticated,
            process[DogRequest](
              from(owner = header[String]("owner"))
            )
          )(
              using(serviceBackend).
                expect[Dog] >>
                respondJson(Ok)
            ),

          handle(
            "noPermDogs",
            Authenticated andThen Authorized("animal"),
            process[DogRequest](
              from(owner = header[String]("owner"))
            )
          )(
              using(serviceBackend).
                expect[Dog] >>
                respondJson(Ok)
            ),

          handle(
            "permDogs",
            Authenticated andThen Authorized("print"),
            process[DogRequest](
              from(owner = header[String]("owner"))
            )
          )(
              using(serviceBackend).
                expect[Dog] >>
                respondJson(Ok)
            )
        )
      }

      case class App()(implicit system: ActorSystem) {
        implicit val mat = ActorMaterializer()
        val started = init(Prefix("/api"))(
          TestController(testServiceBackendRef(system))
        )
      }
    }

  }

}
