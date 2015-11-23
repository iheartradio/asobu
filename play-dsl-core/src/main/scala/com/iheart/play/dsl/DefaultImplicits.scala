package com.iheart.play.dsl

import com.iheart.play.dsl.directives.FallBackDir

object DefaultImplicits {
  implicit val fb: FallBackDir = directives.fallBackTo500
}
