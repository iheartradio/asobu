package asobu.dsl

import asobu.dsl.ControllerMethodBuilder.CombineWithInputArgs
import asobu.dsl.util.HListOps.{RestOf, ToRecord, CombineTo}
import cats.data.Xor._
import play.api.mvc.{Action, EssentialAction, Request, AnyContent}
import shapeless._
import ops.hlist._
import ops.record._

import CatsInstances._
import concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

trait ControllerMethodBuilder {

  object handle {

    def apply[RMT, V <: HList](directive: Directive[RMT])(
      implicit
      combine: CombineWithInputArgs[HNil, V, RMT]
    ) = new handle(Extractor.empty, directive)

    def apply[RMT, ExtractedRepr <: HList, V <: HList](
      extractor: RequestExtractor[ExtractedRepr],
      directive: Directive[RMT]
    )(
      implicit
      combine: CombineWithInputArgs[ExtractedRepr, V, RMT]
    ): handle[RMT, ExtractedRepr, V] = new handle(extractor, directive)
  }

  /**
   *
   * @tparam RMT request message type
   */
  class handle[RMT, ExtractedRepr <: HList, V <: HList](
      extractor: RequestExtractor[ExtractedRepr],
      directive: Directive[RMT]
  )(implicit combine: CombineWithInputArgs[ExtractedRepr, V, RMT]) extends ProductArgs {
    def applyProduct(vs: V): EssentialAction = Action.async { req ⇒
      extractor.run(req).map { extracted ⇒
        val msg: RMT = combine(extracted, vs)
        directive(req.map(_ ⇒ msg))
      }.fold(Future.successful, identity).flatMap(identity)
    }
  }

}

object ControllerMethodBuilder extends ControllerMethodBuilder {
  trait CombineWithInputArgs[ExtractedRepr <: HList, InputArgs <: HList, Out] {
    def apply(extracted: ExtractedRepr, input: InputArgs): Out
  }

  object CombineWithInputArgs {

    implicit def apply[ExtractedRepr <: HList, InputArgs <: HList, Repr <: HList, InputRec <: HList, Out](
      implicit
      lgen: LabelledGeneric.Aux[Out, Repr],
      restOf: RestOf.Aux[Repr, ExtractedRepr, InputRec],
      toRecord: ToRecord[InputArgs, InputRec],
      combineTo: CombineTo[InputRec, ExtractedRepr, Repr]
    ): CombineWithInputArgs[ExtractedRepr, InputArgs, Out] = new CombineWithInputArgs[ExtractedRepr, InputArgs, Out] {
      def apply(extracted: ExtractedRepr, input: InputArgs): Out = lgen.from(combineTo(toRecord(input), extracted))
    }
  }
}
