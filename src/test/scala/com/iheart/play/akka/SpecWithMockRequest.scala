package com.iheart.play.akka

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.mvc.{ Headers, Request }

trait SpecWithMockRequest extends Specification with Mockito {

  def mockReq[T](body: T) = {
    val req = mock[Request[T]]
    req.body returns body
    req
  }
}
