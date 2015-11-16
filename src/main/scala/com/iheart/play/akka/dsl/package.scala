package com.iheart.play.akka

import play.api.mvc.{ Result, Request }
import Directive._

import scala.reflect.ClassTag

package object dsl {

  implicit class DirectiveDSLOps[RMT: ClassTag](d: Directive[RMT]) {
    private val self = new DirectiveOps(d)
    def notFoundIfEmpty[InnerT](extractor: RMT ⇒ Option[InnerT]): Directive[RMT] =
      self.filter(Filters.notFoundIfEmpty(extractor))

  }
}
