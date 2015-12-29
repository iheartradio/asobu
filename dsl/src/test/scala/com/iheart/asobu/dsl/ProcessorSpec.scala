package asobu.dsl

import org.specs2.concurrent.ExecutionEnv
import play.api.libs.json.{JsValue, JsNumber}
import play.api.mvc.{AnyContentAsJson, Result}
import play.api.mvc.Results._
import play.api.test.{FakeRequest, PlaySpecification}

import scala.concurrent.Future
import Syntax._

class ProcessorSpec extends PlaySpecification {
  "construct directive from function" >> { implicit ee: ExecutionEnv ⇒
    val p = Processor[Int, String]((any: Any) ⇒ Future.successful("Success"))
    p(FakeRequest().withBody(1)) must be_==("Success").await
  }

  "channel to a directive" >> { implicit ee: ExecutionEnv ⇒
    val p = Processor.synced[Int, String](_.toString)
    val d = Directive.constant[String](Ok)
    val subject = p combine d

    subject must beAnInstanceOf[Directive[Int]]

  }

}
