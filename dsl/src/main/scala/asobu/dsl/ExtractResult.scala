package asobu.dsl

import asobu.dsl.ExtractResult._
import cats.{Monoid, FlatMap, Monad}
import cats.data.{Xor, XorT}
import cats.syntax.all._
import play.api.mvc.{AnyContent, Request, Result}
import shapeless.{HList, HNil}
import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import CatsInstances._
/**
 * This is basically a replacement of type alias `type ExtractResult[T] = XorT[Future, Result, T]`
 * A concrete class is needed as a work around for Unification and type alias related bugs
 *
 * @param v
 * @tparam T
 */
case class ExtractResult[T](v: XorTF[T]) {
  /**
   * although implicit conversion also give this one, it helps for IDE to know this.
   *
   * @return
   */
  def value: Future[Xor[Result, T]] = v.value

}

object ExtractResult {
  type XorTF[T] = XorT[Future, Result, T]

  @implicitNotFound("need an implicit way of handling Throwable as Result. You can import asobu.dsl.DefaultExtractorImplicits._ for a default simple implementation")
  type FallbackResult = Throwable ⇒ Result

  implicit def toXorTO[T](ox: ExtractResult[T]): XorTF[T] = ox.v
  implicit def toFutureXOr[T](xo: XorTF[T]): ExtractResult[T] = ExtractResult(xo)

  implicit def monad(implicit ex: ExecutionContext): Monad[ExtractResult] = new Monad[ExtractResult] {
    val xm = Monad[XorTF]
    def pure[A](x: A): ExtractResult[A] = xm.pure(x)

    def flatMap[A, B](fa: ExtractResult[A])(f: (A) ⇒ ExtractResult[B]): ExtractResult[B] = xm.flatMap(fa)(f(_))
  }

  def left[T](r: Result)(implicit ex: ExecutionContext): ExtractResult[T] = XorT.left[Future, Result, T](Future.successful(r))

  def pure[T](t: T)(implicit ex: ExecutionContext): ExtractResult[T] = monad.pure(t)

  def fromOption[T](o: Option[T], ifNone: ⇒ Result) = XorT(Future.successful(Xor.fromOption(o, ifNone)))

  def right[T](ft: Future[T])(implicit ex: ExecutionContext) = XorT.right[Future, Result, T](ft)

  def fromTry[T](t: Try[T])(implicit ifFailure: FallbackResult, ex: ExecutionContext): ExtractResult[T] =
    XorT.fromXor[Future](Xor.fromTry(t).leftMap(ifFailure))

  def fromEither[T](fe: Future[Either[Result, T]])(implicit ex: ExecutionContext): ExtractResult[T] = {
    XorT(fe.map(Xor.fromEither))
  }

  def fromXor[T](xor: Xor[Result, T]): ExtractResult[T] = {
    XorT(Future.successful(xor))
  }

}

