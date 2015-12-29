package asobu.dsl

import play.api.mvc.{EssentialAction, Request}
import play.api.test._
import shapeless.HNil
import shapeless.syntax.singleton._
import play.api.mvc.Results._
import scala.concurrent.Future
import Syntax._

object ControllerMethodBuilderSpec extends PlaySpecification {
  import Helpers._

  case class ReqMessage(age: Int, name: String, id: Int)

  "Builder " should {

    "build from header extractor" in {
      val method = handle(
        Extractor(req ⇒ 'name ->> req.headers("name") :: HNil),
        (rm: Request[ReqMessage]) ⇒ Future.successful(Ok(rm.body.name + rm.body.id))
      )

      val req = FakeRequest().withHeaders("name" → "mike")

      val action: EssentialAction = method(12, 23)

      val result = call(action, req)

      contentAsString(result) must be_==("mike23")

    }

    "build directly from director" in {
      val method = handle((rm: Request[ReqMessage]) ⇒ Future.successful(Ok(rm.body.name + rm.body.id)))

      val req = FakeRequest()

      val action: EssentialAction = method(12, "mike", 23)

      val result = call(action, req)

      contentAsString(result) must be_==("mike23")
    }

  }
}
