package asobu

import cats.data.{Kleisli, XorT}
import play.api.mvc.{AnyContent, Request, Result}
import play.core.routing.RouteParams

import scala.annotation.implicitNotFound
import scala.concurrent.Future

package object dsl {

  type Processor[-RMT, +PRT] = Request[RMT] ⇒ Future[PRT]

  type Directive[-RMT] = Processor[RMT, Result]

  type PartialDirective[-RMT] = PartialFunction[Request[RMT], Future[Result]]

  type Filter[-RMT] = (Request[RMT], ⇒ Future[Result]) ⇒ Future[Result]

  type Extractor[TFrom, T] = Kleisli[ExtractResult, TFrom, T]

  type RequestExtractor[T] = Extractor[Request[AnyContent], T]

  type ExtractResult[T] = XorT[Future, Result, T]

  @implicitNotFound("need an implicit way of handling Throwable as Result. You can import asobu.dsl.DefaultExtractorImplicits._ for a default simple implementation")
  type FallbackResult = Throwable ⇒ Result
}

