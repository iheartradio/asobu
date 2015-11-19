package com.iheart.play.akka

import scala.reflect.ClassTag

object Syntax extends ProcessorOps with DirectiveOps {

  implicit class DirectiveDSLOps[RMT: ClassTag](self: Directive[RMT]) {
    def notFoundIfEmpty[InnerT](extractor: RMT ⇒ Option[InnerT]): Directive[RMT] =
      self.filter(Filters.notFoundIfEmpty(extractor))

  }
}
