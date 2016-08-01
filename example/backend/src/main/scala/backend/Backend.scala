package backend

import akka.actor._
import akka.cluster.Cluster
import akka.util.Timeout
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.{EndpointsRegistry, DefaultEndpointsRegistry}
import asobu.distributed.service._
import backend.endpoints.AMixedController
import backend.school.StudentService
import com.iheart.playSwagger.SwaggerSpecGenerator
import com.typesafe.config.ConfigFactory
import backend.factorial._
import meta.BuildInfo
import play.api.libs.json.JsObject
import play.routes.compiler.Route
import scala.collection.JavaConversions._
import scala.collection.immutable.ListMap
import concurrent.duration._
import akka.stream.ActorMaterializer

/**
 * Booting a cluster backend node with all actors
 */
object Backend extends App {

  // Simple cli parsing
  val port = args match {
    case Array()     => "0"
    case Array(port) => port
    case args        => throw new IllegalArgumentException(s"only ports. Args [ $args ] are invalid")
  }

  // System initialization
  val properties = Map(
      "akka.remote.netty.tcp.port" -> port
  )

  implicit val system: ActorSystem = ActorSystem("application", (ConfigFactory parseMap properties)
    .withFallback(ConfigFactory.load())
  )

  // Deploy actors and services
  val factorialBE = FactorialBackend startOn system
  val studentService = system.actorOf(StudentService.props)
  implicit val ao: Timeout = 30.seconds

  lazy val swaggerGenerator = SwaggerSpecGenerator("backend")(getClass.getClassLoader)

  implicit val apiDocGenerator = (prefix: Prefix, routes: Seq[Route]) => {
    val doc: JsObject = swaggerGenerator.generateFromRoutes(ListMap(("backend",(prefix.value, routes))))
    Some(doc)
  }

  implicit val bi: BuildNumber = BuildInfo

  import system.dispatcher

  implicit val mat = ActorMaterializer()(system)

  init(Prefix("/api"))(
    AMixedController(factorialBE, studentService)
  ).onFailure {
    case e => throw e
  }


}
