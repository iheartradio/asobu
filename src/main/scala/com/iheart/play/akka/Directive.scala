package com.iheart.play.akka

import play.api.mvc.{ Action, Result, Request }

import scala.concurrent.Future
import scala.reflect._

object Directive {

  implicit class DirectiveOps[RMT](self: Directive[RMT]) {

    def filter(f: Filter[RMT]): Directive[RMT] = (r: Request[RMT]) ⇒ f(r, self(r))

    def fallback(fallbackTo: Directive[Any])(implicit ct: ClassTag[RMT]): Directive[Any] = { (req: Request[Any]) ⇒
      req.body match {
        case t: RMT if ct.runtimeClass.isInstance(t) ⇒ self(req.map[RMT](_ ⇒ t))
        case t                                       ⇒ fallbackTo(req.map(_ ⇒ t))
      }
    }

  }

  def apply[RMT](pf: PartialFunction[RMT, Future[Result]]): Directive[RMT] =
    (req: Request[RMT]) ⇒ pf(req.body)

}
