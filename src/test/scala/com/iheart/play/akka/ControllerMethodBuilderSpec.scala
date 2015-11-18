package com.iheart.play.akka

import com.iheart.play.akka.ControllerMethodBuilder.Extractor
import play.api.mvc.{ EssentialAction, Request }
import play.api.test._
import shapeless.HNil
import shapeless.syntax.singleton._
import play.api.mvc.Results._
import scala.concurrent.Future

object ControllerMethodBuilderSpec extends PlaySpecification {
  import Helpers._

  case class ReqMessage(id: Int, name: String)

  "Builder " should {

    "build from header extractor" in {
      val method = new ControllerMethodBuilder(
        req ⇒ 'name ->> req.headers("name") :: HNil,
        (rm: Request[ReqMessage]) ⇒ Future.successful(Ok(rm.body.name + rm.body.id))
      )

      val req = FakeRequest().withHeaders("name" → "mike")

      val action: EssentialAction = method(23)

      val result = call(action, req)

      contentAsString(result) must be_==("mike23")

    }

  }
}
