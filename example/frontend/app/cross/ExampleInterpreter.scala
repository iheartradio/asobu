package cross

import javax.inject.{Inject, Singleton}

import api.{Authenticated, ExampleEnricher}
import asobu.distributed.DRequest
import asobu.distributed.gateway.RequestEnricher
import asobu.distributed.gateway.RequestEnricher
import asobu.distributed.gateway.enricher.{RequestEnricher, Interpreter}
import asobu.dsl.{Extractor, RequestExtractor}
import Extractor._
import asobu.dsl.extractors.AuthInfoExtractorBuilder
import play.api.mvc.RequestHeader
import play.api.mvc.Results._

import scala.concurrent.{Future, ExecutionContext}
import com.google.inject.AbstractModule

class ExampleInterpreter extends Interpreter[ExampleEnricher] {

  def apply(ee: ExampleEnricher)(implicit exec: ExecutionContext): RequestEnricher = {
    ee match {
      case Authenticated =>
        Extractor(identity[DRequest]).ensure(Unauthorized("Cannot find userId in header"))((_: DRequest).headers.toMap.contains("user_id"))
    }
  }
}

