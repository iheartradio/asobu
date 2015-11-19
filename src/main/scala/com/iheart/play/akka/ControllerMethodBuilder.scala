package com.iheart.play.akka

import cats.data.Xor._
import com.iheart.play.akka.HListOps.RestOf
import play.api.mvc.{Action, EssentialAction, Request, AnyContent}
import shapeless._
import ops.hlist._
import ops.record._

import ControllerMethodBuilder._
import Syntax._

import scala.concurrent.Future

trait ControllerMethodBuilder {

  case class handle[RMT, ExtractedRepr <: HList, FullRepr <: HList, InputRepr <: HList, V <: HList, K <: HList, TempFull <: HList]
    (extractor: Extractor[ExtractedRepr], directive: Directive[RMT])
    (implicit lgen: LabelledGeneric.Aux[RMT, FullRepr],
     restOf: RestOf.Aux[FullRepr, ExtractedRepr, InputRepr],
     keys: Keys.Aux[InputRepr, K],
     values: Values.Aux[InputRepr, V],
     zip: ZipWithKeys.Aux[K, V, InputRepr],
     prepend: Prepend.Aux[InputRepr, ExtractedRepr, TempFull],
     align: Align[TempFull, FullRepr]) extends ProductArgs {

      import scala.concurrent.ExecutionContext.Implicits.global


      private def combine(inputRepr: InputRepr, extractedRepr: ExtractedRepr): RMT =
        lgen.from(align(inputRepr ++ extractedRepr))


      def applyProduct(vs: V): EssentialAction = Action.async { req ⇒
        val inputRecord: InputRepr = vs.zipWithKeys[K]
        extractor(req).flatMap {
          case Left(result) ⇒ Future.successful(result)
          case Right(extraced) ⇒
            val msg: RMT = combine(inputRecord, extraced)
            directive(req.map(_ ⇒ msg))
        }

      }
    }

}

object ControllerMethodBuilder extends ControllerMethodBuilder {


}
