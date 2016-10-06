package asobu.distributed.service

import asobu.distributed.service.DRequestExtractor.RequestParamsExtractor
import shapeless.HList

/**
 * for convenience in test
 */
object RequestParamsExtractor {
  def apply[L <: HList](implicit builder: RequestParamsExtractorBuilder[L]): RequestParamsExtractor[L] = builder()
}
