package asobu.distributed

import asobu.dsl.ExtractResult._
import asobu.dsl._
import asobu.dsl.extractors.HeaderExtractors
import asobu.dsl.util.Read
import cats.data.{XorT, Xor}
import cats.{Apply, Applicative, Functor}
import cats.sequence.RecordSequencer
import play.api.mvc.Result
import shapeless.ops.hlist._
import shapeless._
import cats.std.future._

import scala.concurrent.{ExecutionContext, Future}

/**
 * Isolates extractor definition from actual extraction logic, will allow api to be more binary compatible
 *
 * @tparam T
 */
trait RequestExtractorDefinition[T] extends (ExecutionContext ⇒ RequestExtractor[T]) with Serializable

object RequestExtractorDefinition extends RequestExtractorDefinitionFunctions with RequestExtractorDefinitionOps {
  type Void = RequestExtractorDefinition[HNil]
}

trait RequestExtractorDefinitionOps {
  implicit class RequestHListExtractorDefinitionOps[L <: HList](self: RequestExtractorDefinition[L]) {
    def and[LB <: HList, LOut <: HList](
      eb: RequestExtractorDefinition[LB]
    )(implicit prepend: Prepend.Aux[L, LB, LOut]): RequestExtractorDefinition[LOut] =
      RequestExtractorDefinition.combine(self, eb)
  }
}

trait RequestExtractorDefinitionFunctions extends PredefinedDefs {
  import cats.syntax.functor._

  implicit def app: Applicative[RequestExtractorDefinition] =
    new Applicative[RequestExtractorDefinition] {
      import LocalExecutionContext.instance //the definition composing always happens locally, and needs to be serializable
      val appR = Applicative[RequestExtractor]

      def ap[A, B](ff: RequestExtractorDefinition[(A) ⇒ B])(fa: RequestExtractorDefinition[A]) =
        new RequestExtractorDefinition[B] {
          def apply(ec: ExecutionContext) = appR.ap(ff(ec))(fa(ec))
        }

      def pure[A](x: A) = new RequestExtractorDefinition[A] {
        def apply(ex: ExecutionContext): RequestExtractor[A] = appR.pure(x)
      }

    }

  val empty: RequestExtractorDefinition[HNil] = new RequestExtractorDefinition[HNil] {
    def apply(ex: ExecutionContext) = RequestExtractor.empty
  }

  def compose = cats.sequence.sequenceRecord

  /**
   * combine two extractors into one that returns a concated list of the two results
   *
   * @return
   */
  def combine[LA <: HList, LB <: HList, LOut <: HList](
    ea: RequestExtractorDefinition[LA],
    eb: RequestExtractorDefinition[LB]
  )(
    implicit
    prepend: Prepend.Aux[LA, LB, LOut]
  ): RequestExtractorDefinition[LOut] = new RequestExtractorDefinition[LOut] {
    def apply(ec: ExecutionContext): RequestExtractor[LOut] = Extractor.combine(ea(ec), eb(ec))
  }

  implicit class RequestExtractorDefinitionOps[T](self: RequestExtractorDefinition[T]) {
    def void: RequestExtractorDefinition[HNil] = self.map(_ ⇒ HNil)
  }

  implicit class RequestExtractorDefinitionOps2[R <: HList](self: RequestExtractorDefinition[R]) {
    def and[ThatR <: HList, ResultR <: HList](that: RequestExtractorDefinition[ThatR])(
      implicit
      prepend: Prepend.Aux[R, ThatR, ResultR]
    ): RequestExtractorDefinition[ResultR] = combine(self, that)
  }

}

object PredefinedDefs {
  @SerialVersionUID(1L)
  case class Header[T: Read](key: String)(implicit fbr: FallbackResult) extends RequestExtractorDefinition[T] {
    def apply(ec0: ExecutionContext) = {
      implicit val ec = ec0
      HeaderExtractors.header(key)
    }
  }
}

trait PredefinedDefs {
  import PredefinedDefs._
  def header[T: Read](key: String)(implicit fbr: FallbackResult): RequestExtractorDefinition[T] = Header[T](key)
}

