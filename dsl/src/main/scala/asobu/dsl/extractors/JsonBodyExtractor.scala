package asobu.dsl.extractors

import asobu.dsl.Extractor
import play.api.libs.json.{JsError, JsSuccess, Reads}
import shapeless.{LabelledGeneric, HList}
import play.api.mvc.Results._
import cats.data.Xor._

import scala.concurrent.Future

class JsonBodyExtractorBuilder[T: Reads] {
  // The name body is chosen for easier syntax
  def body[Repr <: HList](implicit lgen: LabelledGeneric.Aux[T, Repr]): Extractor[Repr] = req ⇒
    Future.successful(req.body.asJson.map(_.validate[T]) match {
      case Some(JsSuccess(t, _)) ⇒
        Right(lgen.to(t))
      case Some(JsError(errors)) ⇒
        Left(BadRequest(errors.seq.mkString(";")))
      case None ⇒
        Left(BadRequest("Invalid JSON body " + req.body.asText))
    })
}

