package controllers


import javax.inject.Inject

import akka.util.Timeout
import asobu.distributed.gateway.ApiDocumentationRegistry.Retrieve
import asobu.distributed.gateway.Gateway
import play.api.cache.Cached
import play.api.libs.concurrent.Execution.Implicits._
import com.iheart.playSwagger.SwaggerSpecGenerator
import play.api.libs.json.JsObject
import play.api.mvc.{RequestHeader, Controller, Action}

import scala.concurrent.Future
import concurrent.duration._
import akka.pattern.ask

class ApiDocuments @Inject() (cached: Cached, gateway: Gateway) extends Controller {
  implicit val cl = getClass.getClassLoader

  val domainPackage = "asobu"
  private lazy val generator = SwaggerSpecGenerator(domainPackage)

  implicit val to: Timeout = 60.seconds

  def specs = cached((_: RequestHeader) => "swaggerDef", 1) {
    Action.async { _ =>
      (for {
        local <- Future.fromTry(generator.generate())
        distributed <- (gateway.apiDocsRegistry ? Retrieve).mapTo[JsObject]
      } yield local deepMerge distributed).map(Ok(_))
    }
  }

}
