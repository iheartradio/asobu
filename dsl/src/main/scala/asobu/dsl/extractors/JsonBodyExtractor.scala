package asobu.dsl.extractors

import asobu.dsl.{Extractor, ExtractResult, RequestExtractor}
import cats.data.XorT
import play.api.libs.json.{JsResult, JsError, JsSuccess, Reads}
import play.api.mvc.{AnyContent, Request}
import shapeless.{LabelledGeneric, HList}
import play.api.mvc.Results._

import scala.concurrent.Future
import ExtractResult._
import asobu.dsl.CatsInstances._
import concurrent.ExecutionContext.Implicits.global

import Extractor._

object JsonBodyExtractor {
  def body[T: Reads]: RequestExtractor[T] = (req: Request[AnyContent]) ⇒
    extractBody(req.body)

  def extractBody[T: Reads](body: AnyContent): ExtractResult[T] =
    body.asJson.map(_.validate[T]) match {
      case Some(JsSuccess(t, _)) ⇒
        pure(t)
      case Some(JsError(errors)) ⇒
        left[T](BadRequest(errors.seq.mkString(";")))
      case None ⇒
        left[T](BadRequest("Invalid JSON body."))
    }

}

