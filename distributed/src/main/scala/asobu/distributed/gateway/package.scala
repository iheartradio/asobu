package asobu.distributed

import asobu.distributed.protocol.DRequest
import asobu.dsl.Extractor

package object gateway {

  type RequestEnricher = Extractor[DRequest, DRequest]

}
