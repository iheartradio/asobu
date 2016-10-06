package asobu.dsl.extractors

import asobu.dsl.{ExtractResult, Extractor, RequestExtractor}
import play.api.mvc.{Request, AnyContent, Results}, Results._

import scala.concurrent.ExecutionContext

trait ExtraExtractors {
  def remoteAddress: RequestExtractor[String] = RequestExtractor(_.remoteAddress)

  def queryString(key: String)(implicit ex: ExecutionContext): RequestExtractor[String] =
    Extractor.of { (r: Request[AnyContent]) ⇒
      ExtractResult.fromOption(r.getQueryString(key), BadRequest(s"Cannot find query parameter $key"))
    }
}
