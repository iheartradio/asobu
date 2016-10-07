package asobu.distributed.service.extractors

import asobu.distributed.protocol.DRequest
import asobu.dsl._
import asobu.dsl.util.Read
import CatsInstances._

import scala.concurrent.ExecutionContext

trait DRequestExtractors {
  import asobu.dsl.extractors.PrimitiveExtractors._
  import asobu.dsl.extractors.HeaderExtractors.missingHeaderException

  def header[T: Read](key: String)(implicit fbr: FallbackResult, ex: ExecutionContext): DRequestExtractor[T] = {
    Extractor((d: DRequest) ⇒ d.headers.toMap.get(key)) andThen stringOption(missingHeaderException(key))
  }
}

object DRequestExtractors extends DRequestExtractors
