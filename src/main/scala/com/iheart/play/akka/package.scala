package com.iheart.play

import play.api.mvc.{ Result, Request }

import scala.concurrent.Future

package object akka {
  type Directive[-RMT] = Request[RMT] ⇒ Future[Result]
  type Processor[-RMT, +PRT] = Request[RMT] ⇒ Future[PRT]
  type Filter[-RMT] = (Request[RMT], ⇒ Future[Result]) ⇒ Future[Result]

}

