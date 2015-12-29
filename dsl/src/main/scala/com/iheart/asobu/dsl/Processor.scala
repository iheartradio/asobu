package asobu.dsl

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

    def combine[T](f: Processor[PRT, T]): Processor[RMT, T] = (req: Request[RMT]) ⇒ {
      self(req).flatMap { (pr: PRT) ⇒
        f(req.map(_ ⇒ pr))
      }
    }

    def contraMap[T](f: T ⇒ RMT): Processor[T, PRT] = Processor.synced(f) combine self
  }

}

