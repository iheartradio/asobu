package com.iheart.play.dsl

import com.iheart.play.dsl.directives.FallbackDir
import com.iheart.play.dsl.extractors.AuthInfoExtractorBuilder
import play.api.libs.json.{JsValue, Json, Writes, Reads}
import play.api.mvc.{RequestHeader, Result}
import shapeless.ops.hlist.{Align, ZipWithKeys}
import shapeless.ops.record.{Values, Keys}
import shapeless.{LabelledGeneric, HList}

import scala.concurrent.Future
import scala.reflect.ClassTag

import SyntaxFacilitators._

trait Syntax
  extends ProcessorOps
  with DirectiveOps
  with ExtractorOps
  with ControllerMethodBuilder
  with cats.syntax.SemigroupSyntax {

  class processorBuilder[RMT] {
    def using[T](t: T)(implicit b: AskableBuilder[T]) = Processor[RMT, Any](b(t))
  }

  def process[RMT] = new processorBuilder[RMT]


  implicit class DirectiveDSL[RMT: ClassTag](self: Directive[RMT]) {

    def `with`(f: Filter[RMT]) = self.filter(f)

    def ifEmpty[InnerT](fieldExtractor: RMT ⇒ Option[InnerT]) = new Object {
      def respond(alternative: Result) = {
        val f: Filter[RMT] = (req, result) ⇒ fieldExtractor(req.body).fold(Future.successful(alternative))(_ ⇒ result)
        self.`with`(f)
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
    private def directive(f: RMT ⇒ Result) = Directive(f)

    def respond(result: Result) = directive(_ ⇒ result)
    def respondJson(tr: JsValue ⇒ Result)(implicit writes: Writes[RMT]) = directive(t ⇒ tr(Json.toJson(t)))
  }

  def from[Repr <: HList] = Extractor.apply[Repr] _

  def fromAuthorized[AuthInfoT](ba: RequestHeader ⇒ Future[Either[String, AuthInfoT]]) = new AuthInfoExtractorBuilder[AuthInfoT](ba)

}

object SyntaxFacilitators {
  type Askable = Any ⇒ Future[Any]

  trait AskableBuilder[T] {
    def apply(t: T): Askable
  }
}

object Syntax extends Syntax
