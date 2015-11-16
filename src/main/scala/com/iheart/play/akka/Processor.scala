package com.iheart.play.akka

import play.api.mvc.Request

import scala.concurrent.Future

object Processor {

  implicit class ProcessorOpts[RMT, PRT](self: Processor[RMT, PRT]) {
    import scala.concurrent.ExecutionContext.Implicits.global

    def flatMap[T](f: Request[PRT] ⇒ Future[T]): Request[RMT] ⇒ Future[T] = (req: Request[RMT]) ⇒ {
      self(req).flatMap { (pr: PRT) ⇒
        f(req.map(_ ⇒ pr))
      }
    }

    def toDirective(afterDirective: Directive[PRT]): Directive[RMT] = flatMap(afterDirective)

    def +[FRT](another: Processor[PRT, FRT]): Processor[RMT, FRT] = flatMap(another)
  }

  def apply[RMT, PRT](f: RMT ⇒ Future[PRT]): Processor[RMT, PRT] =
    (req: Request[RMT]) ⇒ f(req.body)

}
