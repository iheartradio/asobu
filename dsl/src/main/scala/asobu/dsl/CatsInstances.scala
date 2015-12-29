package asobu.dsl

import cats.Monad
import cats.functor.Contravariant

import scala.concurrent.Future

object CatsInstances {

  implicit val futureMonad: Monad[Future] = new Monad[Future] {
    import scala.concurrent.ExecutionContext.Implicits.global

    def pure[A](x: A): Future[A] = Future.successful(x)

    def flatMap[A, B](fa: Future[A])(f: (A) ⇒ Future[B]): Future[B] = fa flatMap f
  }

  implicit def partialFunctionContravariant[R]: Contravariant[PartialFunction[?, R]] =
    new Contravariant[PartialFunction[?, R]] {
      def contramap[T1, T0](pa: PartialFunction[T1, R])(f: T0 ⇒ T1) = new PartialFunction[T0, R] {
        def isDefinedAt(x: T0): Boolean = pa.isDefinedAt(f(x))
        def apply(x: T0): R = pa(f(x))
      }
    }

}
