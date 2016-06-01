package api

import asobu.distributed.{PredefinedDefs, RequestExtractorDefinition}
import asobu.dsl.RequestExtractor
import play.api.mvc.RequestHeader
import shapeless.HNil
import asobu.dsl.extractors.AuthInfoExtractorBuilder

import scala.concurrent.{ExecutionContext, Future}

object authentication {

  case object Authenticated extends RequestExtractorDefinition[String] {
    def apply(ex: ExecutionContext) = new AuthInfoExtractorBuilder({ r: RequestHeader =>
      Future.successful(r.headers.get("UserId").toRight("Cannot find userId in header"))
    }).apply()
  }

}
