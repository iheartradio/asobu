package com.iheart.play

import cats.data.Xor
import play.api.mvc.{AnyContent, Result, Request}
import shapeless.HList

import scala.concurrent.Future

package object dsl {

  type Directive[-RMT] = Request[RMT] ⇒ Future[Result]

  type PartialDirective[-RMT] = PartialFunction[Request[RMT], Future[Result]]

  type Processor[-RMT, +PRT] = Request[RMT] ⇒ Future[PRT]

  type Filter[-RMT] = (Request[RMT], ⇒ Future[Result]) ⇒ Future[Result]

  type Extractor[+ExtractedRepr <: HList] = Request[AnyContent] ⇒ Future[Xor[Result, ExtractedRepr]]


}

