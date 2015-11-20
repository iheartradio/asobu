package com.iheart.play.akka

import com.iheart.play.akka.directives.FallBackDir

object DefaultImplicits {
  implicit val fb: FallBackDir = directives.fallBackTo500
}
