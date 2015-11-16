package com.iheart.play.akka

import play.api.mvc.Result
import play.api.mvc.Results._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ProcessorSpec extends SpecWithMockRequest {

  "construct directive from function" >> {
    val p = Processor[Int, String]((any: Any) ⇒ Future.successful("Success"))
    p(mockReq(1)) must be_==("Success").await
  }

}
