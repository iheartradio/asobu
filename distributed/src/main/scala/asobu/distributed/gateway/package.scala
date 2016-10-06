package asobu.distributed

import asobu.dsl.Extractor

package object gateway {

  type RequestEnricher = Extractor[DRequest, DRequest]

}
