package asobu.dsl

import cats.data.Xor._
import asobu.dsl.HListOps.RestOf
import play.api.mvc.{Action, EssentialAction, Request, AnyContent}
import shapeless._
import ops.hlist._
import ops.record._

import ControllerMethodBuilder._
import Syntax._

import scala.concurrent.Future

trait ControllerMethodBuilder {

  object handle {

    def apply[RMT, FullRepr <: HList, K <: HList, V <: HList](directive: Directive[RMT])(implicit
      lgen: LabelledGeneric.Aux[RMT, FullRepr],
                                                                                         keys:   Keys.Aux[FullRepr, K],
                                                                                         values: Values.Aux[FullRepr, V],
                                                                                         zip:    ZipWithKeys.Aux[K, V, FullRepr],
                                                                                         align:  Align[FullRepr, FullRepr]) =
      new handle(Extractor.empty, directive)

    def apply[RMT, ExtractedRepr <: HList, FullRepr <: HList, InputRepr <: HList, V <: HList, K <: HList, TempFull <: HList](extractor: Extractor[ExtractedRepr], directive: Directive[RMT])(implicit
      lgen: LabelledGeneric.Aux[RMT, FullRepr],
                                                                                                                                                                                             restOf:  RestOf.Aux[FullRepr, ExtractedRepr, InputRepr],
                                                                                                                                                                                             keys:    Keys.Aux[InputRepr, K],
                                                                                                                                                                                             values:  Values.Aux[InputRepr, V],
                                                                                                                                                                                             zip:     ZipWithKeys.Aux[K, V, InputRepr],
                                                                                                                                                                                             prepend: Prepend.Aux[InputRepr, ExtractedRepr, TempFull],
                                                                                                                                                                                             align:   Align[TempFull, FullRepr]): handle[RMT, ExtractedRepr, FullRepr, InputRepr, V, K, TempFull] = new handle(extractor, directive)
  }

  /**
   *
   * @tparam RMT request message type
   */
  class handle[RMT, ExtractedRepr <: HList, FullRepr <: HList, InputRepr <: HList, V <: HList, K <: HList, TempFull <: HList](extractor: Extractor[ExtractedRepr], directive: Directive[RMT])(implicit
    lgen: LabelledGeneric.Aux[RMT, FullRepr],
                                                                                                                                                                                              restOf:  RestOf.Aux[FullRepr, ExtractedRepr, InputRepr],
                                                                                                                                                                                              keys:    Keys.Aux[InputRepr, K],
                                                                                                                                                                                              values:  Values.Aux[InputRepr, V],
                                                                                                                                                                                              zip:     ZipWithKeys.Aux[K, V, InputRepr],
                                                                                                                                                                                              prepend: Prepend.Aux[InputRepr, ExtractedRepr, TempFull],
                                                                                                                                                                                              align:   Align[TempFull, FullRepr]) extends ProductArgs {

    import scala.concurrent.ExecutionContext.Implicits.global

    private def combine(inputRepr: InputRepr, extractedRepr: ExtractedRepr): RMT =
      lgen.from(align(inputRepr ++ extractedRepr))

    def applyProduct(vs: V): EssentialAction = Action.async { req ⇒
      val inputRecord: InputRepr = vs.zipWithKeys[K]
      extractor(req).flatMap {
        case Left(result) ⇒ Future.successful(result)
        case Right(extracted) ⇒
          val msg: RMT = combine(inputRecord, extracted)
          directive(req.map(_ ⇒ msg))
      }

    }
  }

}

object ControllerMethodBuilder extends ControllerMethodBuilder
