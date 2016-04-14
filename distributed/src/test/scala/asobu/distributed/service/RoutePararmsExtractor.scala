package asobu.distributed.service

import asobu.distributed.service.ActionExtractor.RouteParamsExtractor
import shapeless.HList

/**
 * for convenience in test
 */
object RouteParamsExtractor {
  def apply[L <: HList](implicit builder: RouteParamsExtractorBuilder[L]): RouteParamsExtractor[L] = builder()
}
