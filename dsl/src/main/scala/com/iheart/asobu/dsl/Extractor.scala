package asobu.dsl

import cats.data.{Xor, XorT}
import play.api.mvc.Results._
import play.api.mvc.{Result, AnyContent, Request}
import shapeless.ops.hlist.Prepend
import shapeless.{HNil, HList}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import cats.data.Xor._

object Extractor {

  def fromTry[Repr <: HList](f: Request[AnyContent] ⇒ Try[Repr]): Extractor[Repr] = { req ⇒
    Future.successful {
      f(req) match {
        case Success(repr) ⇒ Right(repr)
        case Failure(ex)   ⇒ Left(BadRequest(ex.getMessage))
      }
    }
  }

  val empty: Extractor[HNil] = apply(_ ⇒ HNil)

  def apply[Repr <: HList](f: Request[AnyContent] ⇒ Repr): Extractor[Repr] = fromTry(f andThen (Try[Repr](_)))

  def fromEither[Repr <: HList](f: Request[AnyContent] ⇒ Future[Either[Result, Repr]]): Extractor[Repr] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    f.andThen(_.map(Xor.fromEither))
  }
}

trait ExtractorOps {
  import CatsInstances._

  implicit class extractorOps[Repr <: HList](self: Extractor[Repr]) {
    def and[ThatR <: HList, ResultR <: HList](that: Extractor[ThatR])(
      implicit
      prepend: Prepend.Aux[Repr, ThatR, ResultR]
    ): Extractor[ResultR] = { req ⇒

      (for {
        eitherRepr ← XorT(self(req))
        eitherThatR ← XorT(that(req))
      } yield eitherRepr ++ eitherThatR).value
    }
  }

}

