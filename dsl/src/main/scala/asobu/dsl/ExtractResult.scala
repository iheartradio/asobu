package asobu.dsl

import cats._
import cats.data.{Xor, XorT}
import cats.syntax.all._
import play.api.mvc.{AnyContent, Request, Result}
import shapeless.{HList, HNil}
import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object ExtractResult {
  import CatsInstances._

  def apply[T](fxor: Future[Xor[Result, T]]): ExtractResult[T] =
    XorT(fxor)

  def apply[T](xor: Xor[Result, T]): ExtractResult[T] =
    apply(Future.successful(xor))

  def left[T](r: Result): ExtractResult[T] =
    apply(r.left[T])

  def pure[T](t: T): ExtractResult[T] =
    apply(t.right[Result])

  def fromOption[T](o: Option[T], ifNone: ⇒ Result): ExtractResult[T] =
    apply(Xor.fromOption(o, ifNone))

  def right[T](ft: Future[T])(implicit ex: ExecutionContext): ExtractResult[T] =
    XorT.right[Future, Result, T](ft)

  def fromTry[T](t: Try[T])(implicit ifFailure: FallbackResult): ExtractResult[T] =
    apply(Xor.fromTry(t).leftMap(ifFailure))

  def fromEitherF[T](fe: Future[Either[Result, T]])(implicit ex: ExecutionContext): ExtractResult[T] =
    apply(fe.map(Xor.fromEither))

  def fromEither[T](e: Either[Result, T]): ExtractResult[T] =
    apply(Xor.fromEither(e))

}

