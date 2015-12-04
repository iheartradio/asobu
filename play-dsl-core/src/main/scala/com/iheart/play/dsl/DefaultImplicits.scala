package com.iheart.play.dsl

import com.iheart.play.dsl.directives.FallbackDir

object DefaultImplicits {
  implicit val fb: FallbackDir = directives.fallbackTo500
}
