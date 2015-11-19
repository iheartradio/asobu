package com.iheart.play.akka

import play.api.libs.json.Reads

import scala.reflect.ClassTag

object Syntax extends ProcessorOps with DirectiveOps with ExtractorOps with ControllerMethodBuilder {

  implicit class DirectiveDSLOps[RMT: ClassTag](self: Directive[RMT]) {
    def notFoundIfEmpty[InnerT](extractor: RMT ⇒ Option[InnerT]): Directive[RMT] =
      self.filter(Filters.notFoundIfEmpty(extractor))

  }
  def fromJson[T: Reads] = new extractors.JsonBodyExtractorBuilder[T]

}
