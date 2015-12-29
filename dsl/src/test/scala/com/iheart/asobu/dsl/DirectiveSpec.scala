package asobu.dsl

import Syntax._
import org.specs2.concurrent.ExecutionEnv
import play.api.libs.json.JsString
import play.api.mvc.{Request, Result}
import play.api.mvc.Results._
import play.api.test.{FakeRequest, PlaySpecification}
import scala.concurrent.Future

import scala.reflect._

class DirectiveSpec extends PlaySpecification {
  import scala.concurrent.ExecutionContext.Implicits.global

  "construct directive from partial function" >> { implicit ee: ExecutionEnv ⇒
    val pf: PartialFunction[Any, Future[Result]] = {
      case "expected" ⇒ Future.successful(Ok)
      case _          ⇒ Future.successful(InternalServerError)
    }

    val dir: PartialDirective[Any] = PartialDirective(pf)
    dir(FakeRequest().withBody("expected")) must be_==(Ok).await
    dir(FakeRequest().withBody("something else")) must be_==(InternalServerError).await

  }

  "fallback" >> {
    "fall back to the other directive" >> { implicit ee: ExecutionEnv ⇒
      import Directive._

      val fallbackTo: Directive[Any] = {
        case _ ⇒ Future.successful(InternalServerError)
      }

      case class Boo(foo: String)
      val original: Directive[String] = _ ⇒ Future.successful(Ok)

      val resultDir = original.fallback(fallbackTo)(classTag[String])

      resultDir(FakeRequest().withBody("body")) must be_==(Ok).await
      resultDir(FakeRequest().withBody(1)) must be_==(InternalServerError).await

    }

  }

}
