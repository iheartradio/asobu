package com.iheart.play.akka

import com.iheart.play.akka._
import com.iheart.play.akka.directives.FallBackDir
import play.api.libs.json.{JsValue, Json, Writes, Reads}
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Request, Result}
import shapeless.ops.hlist.{Align, ZipWithKeys}
import shapeless.ops.record.{Values, Keys}
import shapeless.{LabelledGeneric, HList}

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.Try
import cats.syntax.semigroup._

object Syntax
  extends ProcessorOps
  with DirectiveOps
  with ExtractorOps
  with ControllerMethodBuilder
  with cats.syntax.SemigroupSyntax {


  type Askable = Any ⇒ Future[Any]

  trait AskableBuilder[T] {
    def apply(t: T): Askable
  }

  class processorBuilder[RMT] {
    def using[T](t: T)(implicit b: AskableBuilder[T]) = Processor[RMT, Any](b(t))
  }

  def process[RMT] = new processorBuilder[RMT]

//  def ask[RMT, T](t: T)(implicit b: AskableBuilder[T]): Processor[RMT, Any] = Processor[RMT, Any](b(t))

  implicit class DirectiveDSL[RMT: ClassTag](self: Directive[RMT]) {
    def notFoundIfEmpty[InnerT](extractor: RMT ⇒ Option[InnerT]): Directive[RMT] =
      self.filter(filters.notFoundIfEmpty(extractor))
  }

  def fromJson[T: Reads] = new extractors.JsonBodyExtractorBuilder[T]

  implicit class ProcessAnyDSL[RMT](self: Processor[RMT, Any]) {
    def expectAny(pf: PartialFunction[Any, Result]) = self next Directive(pf)
  }

  implicit class FilterDSL[RMT](self: Filter[RMT]) {
    import Filter._
    def and(that: Filter[RMT]) = self combine that
  }

  def expect[T: ClassTag: Writes](tr: JsValue ⇒ Result)(implicit fb: FallBackDir): Directive[Any] =
    Directive((t: T) ⇒ tr(Json.toJson(t))).fallback(fb)

  def from[Repr <: HList] = Extractor.apply[Repr] _

  def handleParams[RMT, FullRepr <: HList, K <: HList, V <: HList]
    (directive: Directive[RMT])
    (implicit lgen: LabelledGeneric.Aux[RMT, FullRepr],
     keys: Keys.Aux[FullRepr, K],
     values: Values.Aux[FullRepr, V],
     zip: ZipWithKeys.Aux[K, V, FullRepr],
     align: Align[FullRepr, FullRepr]) =
     handle(Extractor.empty, directive)


}
