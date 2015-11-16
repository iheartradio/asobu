package com.iheart.play.akka

import play.api.mvc.{ Request, Result }
import play.api.mvc.Results._
import scala.concurrent.Future

import scala.reflect._
import scala.concurrent.ExecutionContext.Implicits.global

class DirectiveSpec extends SpecWithMockRequest {

  "construct directive from partial function" >> {
    val pf: PartialFunction[Any, Future[Result]] = {
      case "expected" ⇒ Future.successful(Ok)
      case _          ⇒ Future.successful(InternalServerError)
    }

    val dir: Directive[Any] = Directive(pf)
    dir(mockReq("expected")) must be_==(Ok).await
    dir(mockReq("something else")) must be_==(InternalServerError).await

  }

  "fallback" >> {
    "fall back to the other directive" >> {
      import Directive._

      val fallbackTo: Directive[Any] = {
        case _ ⇒ Future.successful(InternalServerError)
      }

      case class Boo(foo: String)
      val original: Directive[String] = _ ⇒ Future.successful(Ok)

      val resultDir = original.fallback(fallbackTo)(classTag[String])

      resultDir(mockReq("body")) must be_==(Ok).await
      resultDir(mockReq(3)) must be_==(InternalServerError).await

    }

  }

}
