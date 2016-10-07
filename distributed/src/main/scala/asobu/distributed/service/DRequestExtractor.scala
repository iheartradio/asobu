package asobu.distributed.service

import asobu.distributed.service.extractors.DRequestExtractor
import asobu.distributed.protocol.{RequestParams, DRequest}
import asobu.distributed.service.DRequestExtractor.{RequestParamsExtractor, BodyExtractor}
import asobu.dsl._
import asobu.dsl.extractors.JsonBodyExtractor
import asobu.dsl.util.HListOps.{Combine3To, RestOf, CombineTo, RestOf2}
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
import Extractor._

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

object DRequestExtractor extends ExtractorFunctions {

  type RequestParamsExtractor[T <: HList] = Extractor[RequestParams, T]

  type BodyExtractor[T] = Extractor[AnyContent, T]

  class builder[TMessage] {
    def apply[LParams <: HList, LExtra <: HList, LBody <: HList, TRepr <: HList](
      magnet: BuilderMagnet[LExtra, LBody]
    )(implicit
      gen: LabelledGeneric.Aux[TMessage, TRepr],
      r: RestOf2.Aux[TRepr, LExtra, LBody, LParams],
      combineTo: Combine3To[LExtra, LParams, LBody, TRepr],
      rpeb: RequestParamsExtractorBuilder[LParams],
      ex: ExecutionContext): DRequestExtractor[TMessage] = (dr: DRequest) ⇒ {
      for {
        extras ← magnet.extraExtractor(dr)
        params ← rpeb()(dr.requestParams)
        body ← magnet.bodyExtractor.run(dr.body)
      } yield {
        val repr = combineTo(extras, params, body)
        gen.from(repr)
      }
    }
  }

  def build[TMessage] = new builder[TMessage]

  /**
   * Magnet pattern for overloads
   *
   * @param extraExtractor
   * @param bodyExtractor
   * @tparam LExtra
   * @tparam LBody
   */
  sealed case class BuilderMagnet[LExtra <: HList, LBody <: HList](
    extraExtractor: Extractor[DRequest, LExtra],
    bodyExtractor: BodyExtractor[LBody]
  )

  object BuilderMagnet {
    implicit def fromBoth[LExtra <: HList, LBody <: HList](tuple: (Extractor[DRequest, LExtra], BodyExtractor[LBody])): BuilderMagnet[LExtra, LBody] = BuilderMagnet(tuple._1, tuple._2)

    implicit def fromBody[LBody <: HList](
      bodyExtractor: BodyExtractor[LBody]
    ): BuilderMagnet[HNil, LBody] = BuilderMagnet(empty[DRequest], bodyExtractor)

    implicit def fromExtra[LExtra <: HList](
      extraExtractor: Extractor[DRequest, LExtra]
    ): BuilderMagnet[LExtra, HNil] = BuilderMagnet(extraExtractor, BodyExtractor.empty)

    implicit def fromUnit(u: Unit): BuilderMagnet[HNil, HNil] = BuilderMagnet(empty[DRequest], BodyExtractor.empty)

  }

}

object BodyExtractor {
  val empty = Extractor.empty[AnyContent]
  def json[T: Reads]: BodyExtractor[T] = Extractor.of(JsonBodyExtractor.extractBody[T])
}

@implicitNotFound("Cannot construct RouteParamsExtractor out of ${L}")
trait RequestParamsExtractorBuilder[L <: HList] extends (() ⇒ RequestParamsExtractor[L]) with Serializable

trait MkRequestParamsExtractorBuilder0 {

  //todo: this extract from either path or query without a way to specify one way or another.

  trait kvToKlesili1 extends Poly1 {
    implicit def caseKV[K <: Symbol, V](
      implicit
      binder: QueryStringBindable[V],
      ex: ExecutionContext
    ): Case.Aux[FieldKV[K, V], FieldType[K, Extractor[RequestParams, V]]] =
      at[FieldKV[K, V]] { kv ⇒
        field[K](queryExtractor(kv.name))
      }

    def missingFieldResult(field: String) = Json.obj("error" → s"missing field $field")

    def formatError[V](either: Either[String, V]) = either.leftMap(error ⇒ Json.obj("error" → s"can't parse field $field $error"))

    def pathExtractor[V](field: String)(implicit
      binder: PathBindable[V],
      ex: ExecutionContext): Extractor[RequestParams, V] = (params: RequestParams) ⇒
      for {
        strV ← ExtractResult.fromOption(params.pathParams.get(field), BadRequest(missingFieldResult(field)))
        r ← ExtractResult.fromEither(formatError(binder.bind(field, strV)).leftMap(BadRequest(_)))
      } yield r

    def queryExtractor[V](field: String)(implicit
      binder: QueryStringBindable[V],
      ex: ExecutionContext): Extractor[RequestParams, V] = (params: RequestParams) ⇒ {
      val v = binder.bind(field, params.queryString)
      val result = v.map(formatError).getOrElse(Left(missingFieldResult(field))).leftMap(BadRequest(_))
      ExtractResult(Future.successful(Xor.fromEither(result)))
    }

  }

  trait kvToKlesili0 extends kvToKlesili1 {
    implicit def caseKV[K <: Symbol, V](
      implicit
      binder: PathBindable[V],
      ex: ExecutionContext
    ): Case.Aux[FieldKV[K, V], FieldType[K, Extractor[RequestParams, V]]] =
      at[FieldKV[K, V]] { kv ⇒ field[K](pathExtractor(kv.name)) }
  }

  object kvToKlesili extends kvToKlesili0 {
    implicit def caseKV[K <: Symbol, V](
      implicit
      pathBinder: PathBindable[V],
      queryBinder: QueryStringBindable[V],
      ex: ExecutionContext
    ): Case.Aux[FieldKV[K, V], FieldType[K, Extractor[RequestParams, V]]] =
      at[FieldKV[K, V]] { kv ⇒
        field[K](pathExtractor(kv.name) orElse queryExtractor(kv.name))
      }
  }

  implicit def autoMkForRecord[Repr <: HList, KVs <: HList, KleisliRepr <: HList]( //todo use traverse here
    implicit
    ks: FieldKVs.Aux[Repr, KVs],
    mapper: Mapper.Aux[kvToKlesili.type, KVs, KleisliRepr],
    sequence: RecordSequencer[KleisliRepr]
  ): RequestParamsExtractorBuilder[Repr] = new RequestParamsExtractorBuilder[Repr] {
    def apply(): RequestParamsExtractor[Repr] =
      sequence(ks().map(kvToKlesili)).asInstanceOf[RequestParamsExtractor[Repr]] //this cast is needed because a RecordSequencer.Aux can't find the Repr if Repr is not explicitly defined. The cast is safe because the mapper guarantee the result type. todo: find a way to get rid of the cast
  }

}

object RequestParamsExtractorBuilder extends MkRequestParamsExtractorBuilder0 {
  def apply[T <: HList](implicit rpe: RequestParamsExtractorBuilder[T]): RequestParamsExtractorBuilder[T] = rpe

  implicit val empty: RequestParamsExtractorBuilder[HNil] = new RequestParamsExtractorBuilder[HNil] {
    def apply() = Extractor.empty[RequestParams]
  }
}

