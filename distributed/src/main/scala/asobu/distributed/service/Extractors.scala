package asobu.distributed.service

import asobu.distributed.service.Action.DistributedRequest
import asobu.distributed.service.Extractors._
import asobu.distributed.RequestExtractorDefinition
import asobu.distributed.gateway.SyncedExtractResult
import asobu.distributed.service.Action.DistributedRequest
import asobu.distributed.service.Extractors.{RouteParamsExtractor, BodyExtractor, RemoteExtractor}
import asobu.dsl._
import asobu.dsl.extractors.JsonBodyExtractor
import asobu.dsl.util.HListOps.{RestOf, CombineTo, RestOf2}
import cats.{Monad, Functor, Eval}
import cats.sequence.RecordSequencer
import shapeless.ops.hlist.Prepend
import asobu.dsl.util.RecordOps.{FieldKV, FieldKVs}
import cats.data.{Xor, Kleisli}
import play.api.libs.json.{Reads, Json}
import play.core.routing.RouteParams
import shapeless.labelled.FieldType
import shapeless.ops.hlist.Mapper
import shapeless._, labelled.field
import ExtractResult._
import cats.syntax.all._
import CatsInstances._
import play.api.mvc._, play.api.mvc.Results._

import scala.annotation.implicitNotFound
import scala.concurrent.Future

trait Extractors[TMessage] {
  /**
   * List to be extracted from request and send to the remote handler
   */
  type LToSend <: HList
  type LParam <: HList
  type LExtra <: HList

  val remoteExtractorDef: RemoteExtractorDef[LToSend, LParam, LExtra]

  def localExtract(dr: DistributedRequest[LToSend]): ExtractResult[TMessage]
}

object Extractors {
  type Aux[TMessage, LToSend0 <: HList, LParam0 <: HList, LExtra0 <: HList] = Extractors[TMessage] {
    type LToSend = LToSend0
    type LParam = LParam0
    type LExtra = LExtra0
  }

  type RouteParamsExtractor[T] = Kleisli[SyncedExtractResult, RouteParams, T]

  /**
   * Extract information at the gateway end
   */
  type RemoteExtractor[T] = Extractor[(RouteParams, Request[AnyContent]), T]

  type BodyExtractor[T] = Extractor[AnyContent, T]

  class builder[TMessage] {

    def apply[LExtracted <: HList, LParamExtracted <: HList, LRemoteExtra <: HList, LBody <: HList, TRepr <: HList](
      remoteRequestExtractorDefs: RequestExtractorDefinition[LRemoteExtra],
      bodyExtractor: BodyExtractor[LBody]
    )(implicit
      gen: LabelledGeneric.Aux[TMessage, TRepr],
      r: RestOf2.Aux[TRepr, LRemoteExtra, LBody, LParamExtracted],
      prepend: Prepend.Aux[LParamExtracted, LRemoteExtra, LExtracted],
      combineTo: CombineTo[LExtracted, LBody, TRepr],
      rpeb: RouteParamsExtractorBuilder[LParamExtracted]): Extractors.Aux[TMessage, LExtracted, LParamExtracted, LRemoteExtra] = new Extractors[TMessage] {

      type LToSend = LExtracted
      type LParam = LParamExtracted
      type LExtra = LRemoteExtra
      val remoteExtractorDef = RemoteExtractorDef(rpeb, remoteRequestExtractorDefs)

      def localExtract(dr: DistributedRequest[LToSend]): ExtractResult[TMessage] = {
        import scala.concurrent.ExecutionContext.Implicits.global
        bodyExtractor.run(dr.body).map { body ⇒
          val repr = combineTo(dr.extracted, body)
          gen.from(repr)
        }
      }
    }

    def apply[LParamExtracted <: HList, LBody <: HList, TRepr <: HList](
      bodyExtractor: BodyExtractor[LBody]
    )(implicit
      gen: LabelledGeneric.Aux[TMessage, TRepr],
      r: RestOf.Aux[TRepr, LBody, LParamExtracted],
      combineTo: CombineTo[LParamExtracted, LBody, TRepr],
      rpeb: RouteParamsExtractorBuilder[LParamExtracted]): Extractors.Aux[TMessage, LParamExtracted, LParamExtracted, HNil] = apply(RequestExtractorDefinition.empty, bodyExtractor)

    def apply[TRepr <: HList]()(
      implicit
      gen: LabelledGeneric.Aux[TMessage, TRepr],
      combineTo: CombineTo[TRepr, HNil, TRepr],
      rpeb: RouteParamsExtractorBuilder[TRepr]
    ): Extractors.Aux[TMessage, TRepr, TRepr, HNil] = apply(BodyExtractor.empty)
  }

  def build[TMessage] = new builder[TMessage]

}

@SerialVersionUID(1L)
case class RemoteExtractorDef[LExtracted <: HList, LParamExtracted <: HList, LRemoteExtra <: HList](
    routeParamsExtractorBuilder: RouteParamsExtractorBuilder[LParamExtracted],
    requestExtractorDefinition: RequestExtractorDefinition[LRemoteExtra]
)(implicit val prepend: Prepend.Aux[LParamExtracted, LRemoteExtra, LExtracted]) {

  /**
   * Cannot be lazy val which causes it to be mutable
   *
   * @return
   */
  def extractor: RemoteExtractor[LExtracted] = {
    val rpe = routeParamsExtractorBuilder()
    Extractor.zip(rpe.mapF(r ⇒ fromXor(r.v)), requestExtractorDefinition.apply())
  }

}

object BodyExtractor {
  val empty = Extractor.empty[AnyContent]
  def json[T: Reads]: BodyExtractor[T] = Extractor.fromFunction(JsonBodyExtractor.extractBody[T])

}

@implicitNotFound("Cannot construct RouteParamsExtractor out of ${L}")
trait RouteParamsExtractorBuilder[L <: HList] extends (() ⇒ RouteParamsExtractor[L]) with Serializable

trait MkRouteParamsExtractorBuilder0 {

  //todo: this extract from either path or query without a way to specify one way or another.
  object kvToKlesili extends Poly1 {
    implicit def caseKV[K <: Symbol, V: RouteParamRead](
      implicit
      w: Witness.Aux[K]
    ): Case.Aux[FieldKV[K, V], FieldType[K, Kleisli[SyncedExtractResult, RouteParams, V]]] =
      at[FieldKV[K, V]] { kv ⇒
        field[K](Kleisli[SyncedExtractResult, RouteParams, V] { (params: RouteParams) ⇒
          val field: String = w.value.name
          val pe = RouteParamRead[V]
          val extracted = pe(field, params)
          extracted.leftMap(m ⇒ BadRequest(Json.obj("error" → s"missing field $field $m")))
        })
      }
  }

  implicit def autoMkForRecord[Repr <: HList, KVs <: HList, KleisliRepr <: HList](
    implicit
    ks: FieldKVs.Aux[Repr, KVs],
    mapper: Mapper.Aux[kvToKlesili.type, KVs, KleisliRepr],
    sequence: RecordSequencer[KleisliRepr]
  ): RouteParamsExtractorBuilder[Repr] = new RouteParamsExtractorBuilder[Repr] {
    def apply(): RouteParamsExtractor[Repr] =
      sequence(ks().map(kvToKlesili)).asInstanceOf[Kleisli[SyncedExtractResult, RouteParams, Repr]] //this cast is needed because a RecordSequencer.Aux can't find the Repr if Repr is not explicitly defined. The cast is safe because the mapper guarantee the result type. todo: find a way to get rid of the cast
  }

}

object RouteParamsExtractorBuilder extends MkRouteParamsExtractorBuilder0 {

  def apply[T <: HList](implicit rpe: RouteParamsExtractorBuilder[T]): RouteParamsExtractorBuilder[T] = rpe

  implicit val empty: RouteParamsExtractorBuilder[HNil] = new RouteParamsExtractorBuilder[HNil] {
    def apply() = Kleisli[SyncedExtractResult, RouteParams, HNil](_ ⇒ Xor.right(HNil))
  }
}

trait RouteParamRead[V] extends Serializable {
  def apply(field: String, params: RouteParams): Xor[String, V]
}

//This whole thing is needed because PathBindable and QueryStringBindable isn't serializable
object RouteParamRead {

  def apply[V](implicit rpe: RouteParamRead[V]) = rpe

  def mk[V](f: ⇒ (String, RouteParams) ⇒ Xor[String, V]) = new RouteParamRead[V] {
    def apply(field: String, params: RouteParams): Xor[String, V] = {
      f(field, params)
    }
  }

  implicit val rpeInt: RouteParamRead[Int] = mk(ext[Int])
  implicit val rpeLong: RouteParamRead[Long] = mk(ext[Long])
  implicit val rpeDouble: RouteParamRead[Double] = mk(ext[Double])
  implicit val rpeFloat: RouteParamRead[Float] = mk(ext[Float])
  implicit val rpeBoolean: RouteParamRead[Boolean] = mk(ext[Boolean])
  implicit val rpeString: RouteParamRead[String] = mk(ext[String])

  def ext[V: PathBindable: QueryStringBindable] = (field: String, params: RouteParams) ⇒ {
    params.fromPath[V](field).value.toXor orElse params.fromQuery[V](field).value.toXor
  }
}
