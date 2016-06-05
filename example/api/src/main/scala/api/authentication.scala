package api

import asobu.distributed.{CustomRequestExtractorDefinition, PredefinedDefs, RequestExtractorDefinition}
import asobu.dsl.RequestExtractor
import play.api.mvc.RequestHeader
import shapeless.HNil
import asobu.dsl.extractors.AuthInfoExtractorBuilder

import scala.concurrent.{ExecutionContext, Future}

object authentication {

  case object Authenticated extends CustomRequestExtractorDefinition[String]

}
