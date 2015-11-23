package com.iheart.play.akka

import play.api.mvc.Request

import scala.concurrent.Future

object Processor {

  def identity[T]: Processor[T, T] = r ⇒ Future.successful(r.body)

  def apply[RMT, PRT](f: RMT ⇒ Future[PRT]): Processor[RMT, PRT] =
    (req: Request[RMT]) ⇒ f(req.body)

  def synced[RMT, PRT](f: RMT ⇒ PRT): Processor[RMT, PRT] =
    apply(f andThen Future.successful)

}

trait ProcessorOps {

  implicit class processorOps[-RMT, +PRT](self: Processor[RMT, PRT]) {
    import scala.concurrent.ExecutionContext.Implicits.global

    def flatMap[T](f: Request[PRT] ⇒ Future[T]): Request[RMT] ⇒ Future[T] = (req: Request[RMT]) ⇒ {
      self(req).flatMap { (pr: PRT) ⇒
        f(req.map(_ ⇒ pr))
      }
    }

    def next[FRT](another: Processor[PRT, FRT]): Processor[RMT, FRT] = flatMap(another)
  }

}

