package com.iheart.play.dsl

import cats.Monad

import scala.concurrent.Future

object ScalaCatsInstances {

  implicit val futureMonad: Monad[Future] = new Monad[Future] {
    import scala.concurrent.ExecutionContext.Implicits.global

    def pure[A](x: A): Future[A] = Future.successful(x)

    def flatMap[A, B](fa: Future[A])(f: (A) ⇒ Future[B]): Future[B] = fa flatMap f
  }

}
