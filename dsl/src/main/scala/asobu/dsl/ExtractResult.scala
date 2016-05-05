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

  def left[T](r: Result)(implicit ex: ExecutionContext): ExtractResult[T] =
    XorT.left[Future, Result, T](Future.successful(r))

  def pure[T](t: T)(implicit ex: ExecutionContext): ExtractResult[T] =
    XorT.pure[Future, Result, T](t)

  def fromOption[T](o: Option[T], ifNone: ⇒ Result)(implicit ex: ExecutionContext): ExtractResult[T] =
    fromXor(Xor.fromOption(o, ifNone))

  def right[T](ft: Future[T])(implicit ex: ExecutionContext) = XorT.right[Future, Result, T](ft)

  def fromTry[T](t: Try[T])(implicit ifFailure: FallbackResult, ex: ExecutionContext): ExtractResult[T] =
    fromXor(Xor.fromTry(t).leftMap(ifFailure))

  def fromEither[T](fe: Future[Either[Result, T]])(implicit ex: ExecutionContext): ExtractResult[T] =
    XorT(fe.map(Xor.fromEither))

  def fromXor[T](xor: Xor[Result, T])(implicit ex: ExecutionContext): ExtractResult[T] =
    XorT.fromXor[Future](xor)

}

