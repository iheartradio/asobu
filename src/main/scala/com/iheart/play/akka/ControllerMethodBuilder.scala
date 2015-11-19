package com.iheart.play.akka

import com.iheart.play.akka.ControllerMethodBuilder.Extractor
import play.api.mvc.{Action, EssentialAction, Request, AnyContent}
import shapeless._
import ops.hlist._
import ops.record._

import ControllerMethodBuilder._

import Syntax._

class ControllerMethodBuilder[RMT, ExtractedRepr <: HList, FullRepr <: HList, InputRepr <: HList, V <: HList, K <: HList, TempFull <: HList]
  (extractor: Extractor[ExtractedRepr],
   directive: Directive[RMT])
  (implicit lgen: LabelledGeneric.Aux[RMT, FullRepr],
            restOf: RestOf.Aux[FullRepr, ExtractedRepr, InputRepr],
            keys: Keys.Aux[InputRepr, K],
            values: Values.Aux[InputRepr, V],
            zip: ZipWithKeys.Aux[K, V, InputRepr],
            prepend: Prepend.Aux[InputRepr, ExtractedRepr, TempFull],
            align: Align[TempFull, FullRepr]) extends ProductArgs {

  private def combine(inputRepr: InputRepr, extractedRepr: ExtractedRepr): RMT =
    lgen.from(align(inputRepr ++ extractedRepr))


  def applyProduct(vs: V): EssentialAction = Action.async { req ⇒
    val inputRecord: InputRepr = vs.zipWithKeys[K]
    val msg: RMT = combine(inputRecord, extractor(req))
    directive(req.map(_ ⇒ msg))
  }
}

object ControllerMethodBuilder {

  type Extractor[ExtractedRepr <: HList] = Request[AnyContent] ⇒ ExtractedRepr

  trait RestOf[L <: HList, SL <: HList] {
    type Out <: HList
  }

  object RestOf {

    type Aux[L <: HList, SL <: HList, Out0 <: HList] = RestOf[L, SL] {
      type Out = Out0
    }

    implicit def hlistRestOfNil[L <: HList]: Aux[L, HNil, L] = new RestOf[L, HNil] { type Out = L }

    implicit def hlistRestOf[L <: HList, E, RemE <: HList, Rem <: HList, SLT <: HList](implicit rt: Remove.Aux[L, E, (E, RemE)], st: Aux[RemE, SLT, Rem]): Aux[L, E :: SLT, Rem] =
      new RestOf[L, E :: SLT] { type Out = Rem }
  }
}
