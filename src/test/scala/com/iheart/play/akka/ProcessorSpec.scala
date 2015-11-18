package com.iheart.play.akka

import org.specs2.concurrent.ExecutionEnv
import play.api.libs.json.{ JsValue, JsNumber }
import play.api.mvc.{ AnyContentAsJson, Result }
import play.api.mvc.Results._
import play.api.test.{ FakeRequest, PlaySpecification }

import scala.concurrent.Future

class ProcessorSpec extends PlaySpecification {
  "construct directive from function" >> { implicit ee: ExecutionEnv ⇒
    val p = Processor[Int, String]((any: Any) ⇒ Future.successful("Success"))
    p(FakeRequest().withBody(1)) must be_==("Success").await
  }

}
