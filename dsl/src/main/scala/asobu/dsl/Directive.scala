package asobu.dsl

import play.api.mvc.{Result, Request}
import play.api.mvc.Results._

import scala.concurrent.Future
import scala.reflect._
import CatsInstances._
import cats.std.function._
import cats.syntax.contravariant._

object Directive {

  def constant[RMT](r: Result): Directive[RMT] = _ ⇒ Future.successful(r)

  def apply[RMT](f: RMT ⇒ Result): Directive[RMT] = (f andThen Future.successful).contramap[Request[RMT]](_.body)

}

object PartialDirective {

  def apply[RMT](pf: PartialFunction[RMT, Future[Result]]): PartialDirective[RMT] = pf.contramap[Request[RMT]](_.body)

  def synced[RMT](pf: PartialFunction[RMT, Result]): PartialDirective[RMT] = apply(pf andThen (Future.successful))

}

trait DirectiveOps {

  implicit class directiveOps[RMT](self: Directive[RMT]) {

    def filter(f: Filter[RMT]): Directive[RMT] = (r: Request[RMT]) ⇒ f(r, self(r))

    def fallback(fallbackTo: Directive[Any])(implicit ct: ClassTag[RMT]): Directive[Any] = { (req: Request[Any]) ⇒
      req.body match {
        case t: RMT if ct.runtimeClass.isInstance(t) ⇒ self(req.map[RMT](_ ⇒ t))
        case t                                       ⇒ fallbackTo(req.map(_ ⇒ t))
      }
    }

  }
}
