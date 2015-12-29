package asobu

import cats.data.Xor
import play.api.mvc.{AnyContent, Result, Request}
import shapeless.HList

import scala.concurrent.Future

package object dsl {

  type Processor[-RMT, +PRT] = Request[RMT] ⇒ Future[PRT]

  type Directive[-RMT] = Processor[RMT, Result]

  type PartialDirective[-RMT] = PartialFunction[Request[RMT], Future[Result]]

  type Filter[-RMT] = (Request[RMT], ⇒ Future[Result]) ⇒ Future[Result]

  type Extractor[+ExtractedRepr <: HList] = Request[AnyContent] ⇒ Future[Xor[Result, ExtractedRepr]]

}

