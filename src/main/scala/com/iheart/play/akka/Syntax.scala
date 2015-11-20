package com.iheart.play.akka

import com.iheart.play.akka._
import com.iheart.play.akka.directives.FallBackDir
import play.api.libs.json.Reads
import play.api.mvc.{AnyContent, Request, Result}
import shapeless.ops.hlist.{Align, ZipWithKeys}
import shapeless.ops.record.{Values, Keys}
import shapeless.{LabelledGeneric, HList}

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.Try

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
    def respondWith(pf: PartialFunction[Any, Result]) = self >> Directive(pf)
  }

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
