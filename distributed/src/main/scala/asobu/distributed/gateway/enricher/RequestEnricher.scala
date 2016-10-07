package asobu.distributed.gateway.enricher

import asobu.distributed.protocol.DRequest
import asobu.distributed.gateway.RequestEnricher
import asobu.dsl.{ExtractResult, Extractor}
import Extractor._
import play.api.mvc.Result

object RequestEnricher {
  def apply(f: DRequest ⇒ Either[Result, DRequest]): RequestEnricher = f andThen ExtractResult.fromEither
}
