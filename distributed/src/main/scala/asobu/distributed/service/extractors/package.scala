package asobu.distributed.service

import asobu.distributed.DRequest
import asobu.dsl.Extractor

package object extractors {
  type DRequestExtractor[T] = Extractor[DRequest, T]
}
