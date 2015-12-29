package asobu.dsl

import asobu.dsl.directives.FallbackDir
import asobu.dsl.extractors.AuthInfoExtractorBuilder
import play.api.libs.json.{JsValue, Json, Writes, Reads}
import play.api.mvc.{RequestHeader, Result, Results}
import shapeless.HList

import scala.concurrent.Future
import scala.reflect.ClassTag

import SyntaxFacilitators._

trait CompositionSyntax
  extends ProcessorOps
  with DirectiveOps
  with ExtractorOps
  with cats.syntax.SemigroupSyntax {

  class processorBuilder[RMT] {
    def using[T](t: T)(implicit b: AskableBuilder[T]) = Processor[RMT, Any](b(t))
  }

  def process[RMT] = new processorBuilder[RMT]

  implicit class DirectiveDSL[RMT: ClassTag](self: Directive[RMT]) {

    def `with`(f: Filter[RMT]) = self.filter(f)

    case class ifEmpty[InnerT](fieldExtractor: RMT ⇒ Option[InnerT]) {
      def respond(alternative: Result) = {
        val f: Filter[RMT] = (req, result) ⇒ fieldExtractor(req.body).fold(Future.successful(alternative))(_ ⇒ result)
        self.filter(f)
      }
    }
  }

  def using[RMT](filters: Filter[Any]*)(directive: Directive[RMT]): Directive[RMT] =
    directive.filter(filters.reduce(_ and _))

  def fromJson[T: Reads] = new extractors.JsonBodyExtractorBuilder[T]

  implicit class ProcessAnyDSL[RMT, PRT](self: Processor[RMT, PRT]) {
    def expectAny(pf: PartialFunction[Any, Result]) = self combine Directive(pf)

    def next[RT: ClassTag](d: Directive[RT])(implicit fb: FallbackDir) = self combine (d fallback fb)
    def >>[RT: ClassTag](d: Directive[RT])(implicit fb: FallbackDir) = next(d)
  }

  implicit class FilterDSL[RMT](self: Filter[RMT]) {
    import Filter._
    def and(that: Filter[RMT]) = self combine that
    def >>[T <: RMT](directive: Directive[T]): Directive[T] = apply(directive)
    def apply[T <: RMT](directive: Directive[T]): Directive[T] = directive.filter(self)
  }

  case class expect[RMT: ClassTag]() {
    def respond(f: RMT ⇒ Result): Directive[RMT] = Directive(f)
    def respond(result: Result): Directive[RMT] = respond(_ ⇒ result)
    def respondJson(tr: JsValue ⇒ Result)(implicit writes: Writes[RMT]): Directive[RMT] = respond(t ⇒ tr(Json.toJson(t)))
    def respondJson(r: Results#Status)(implicit writes: Writes[RMT]): Directive[RMT] = respond(t ⇒ r.apply(Json.toJson(t)))
  }

  def from[Repr <: HList] = Extractor.apply[Repr] _

  def fromAuthorized[AuthInfoT](ba: RequestHeader ⇒ Future[Either[String, AuthInfoT]]) = new AuthInfoExtractorBuilder[AuthInfoT](ba)

}

trait Syntax extends CompositionSyntax with ControllerMethodBuilder

object SyntaxFacilitators {
  type Askable = Any ⇒ Future[Any]

  trait AskableBuilder[T] {
    def apply(t: T): Askable
  }
}

object Syntax extends Syntax
