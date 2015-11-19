package com.iheart.play.akka

import play.api.mvc.{ Action, Result, Request }

import scala.concurrent.Future
import scala.reflect._

object Directive {

  def constant[RMT](r: Result): Directive[RMT] = _ ⇒ Future.successful(r)

  def apply[RMT](pf: PartialFunction[RMT, Future[Result]]): Directive[RMT] = (req: Request[RMT]) ⇒ pf(req.body)

  def apply[RMT](f: RMT ⇒ Result): Directive[RMT] = apply(PartialFunction(f andThen Future.successful))

}

trait DirectiveOps {

  implicit class ops[RMT](self: Directive[RMT]) {

    def filter(f: Filter[RMT]): Directive[RMT] = (r: Request[RMT]) ⇒ f(r, self(r))

    def fallback(fallbackTo: Directive[Any])(implicit ct: ClassTag[RMT]): Directive[Any] = { (req: Request[Any]) ⇒
      req.body match {
        case t: RMT if ct.runtimeClass.isInstance(t) ⇒ self(req.map[RMT](_ ⇒ t))
        case t                                       ⇒ fallbackTo(req.map(_ ⇒ t))
      }
    }

  }
}
