package asobu.distributed.gateway

import asobu.dsl.CatsInstances._
import cats.Monad
import cats.data.Xor
import play.api.mvc.Result
import language.implicitConversions

/**
 * This is basically a replacement of type alias `type SyncedExtractResult[T] = Xor[Result, T]`
 * A concrete class is needed as a work around for Unification and type alias related bugs
 *
 * @param v
 * @tparam T
 */
case class SyncedExtractResult[T](v: Result Xor T)

object SyncedExtractResult {

  implicit def toXorT[T](ox: SyncedExtractResult[T]): Result Xor T = ox.v
  implicit def toSyncedResult[T](xo: Result Xor T): SyncedExtractResult[T] = SyncedExtractResult(xo)

  implicit def monad: Monad[SyncedExtractResult] = new Monad[SyncedExtractResult] {
    val xm = Monad[Result Xor ?]
    def pure[A](x: A): SyncedExtractResult[A] = xm.pure(x)

    def flatMap[A, B](fa: SyncedExtractResult[A])(f: (A) ⇒ SyncedExtractResult[B]): SyncedExtractResult[B] = xm.flatMap(fa)(f(_))
  }

}
