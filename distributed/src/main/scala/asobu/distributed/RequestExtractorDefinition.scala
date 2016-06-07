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
import CustomRequestExtractorDefinition.Interpreter
import scala.concurrent.{ExecutionContext, Future}

/**
 * Isolates extractor definition from actual extraction logic, will allow api to be more binary compatible
 *
 * @tparam T
 */
trait RequestExtractorDefinition[T] extends Serializable {
  def apply(interpreter: Interpreter)(implicit ec: ExecutionContext): RequestExtractor[T]
}

trait CustomRequestExtractorDefinition[T] extends RequestExtractorDefinition[T] {
  final def apply(interpreter: Interpreter)(implicit ec: ExecutionContext): RequestExtractor[T] =
    interpreter.interpret(this)
}

object CustomRequestExtractorDefinition {
  trait Interpreter {
    def interpret[T](cred: CustomRequestExtractorDefinition[T]): RequestExtractor[T]
  }
  val interpreterClassConfigKey = "extractorDefinitionInterpreterClass"
}

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

      val appR = {
        import LocalExecutionContext.instance //the definition composing always happens locally, and needs to be serializable
        Applicative[RequestExtractor]
      }

      def ap[A, B](ff: RequestExtractorDefinition[(A) ⇒ B])(fa: RequestExtractorDefinition[A]): RequestExtractorDefinition[B] =
        new RequestExtractorDefinition[B] {
          def apply(interpreter: Interpreter)(implicit ec: ExecutionContext) = appR.ap(ff(interpreter))(fa(interpreter))
        }

      def pure[A](x: A): RequestExtractorDefinition[A] = new RequestExtractorDefinition[A] {
        def apply(interpreter: Interpreter)(implicit ex: ExecutionContext): RequestExtractor[A] = appR.pure(x)
      }

    }

  val empty: RequestExtractorDefinition[HNil] = new RequestExtractorDefinition[HNil] {
    def apply(interpreter: Interpreter)(implicit ex: ExecutionContext) = RequestExtractor.empty
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
    def apply(interpreter: Interpreter)(implicit ec: ExecutionContext): RequestExtractor[LOut] =
      Extractor.combine(ea(interpreter), eb(interpreter))
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
    def apply(interpreter: Interpreter)(implicit ec: ExecutionContext) = {
      HeaderExtractors.header(key)
    }
  }
}

trait PredefinedDefs {
  import PredefinedDefs._
  def header[T: Read](key: String)(implicit fbr: FallbackResult): RequestExtractorDefinition[T] = Header[T](key)
}

