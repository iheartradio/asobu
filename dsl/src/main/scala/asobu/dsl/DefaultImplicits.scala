package asobu.dsl

import asobu.dsl.directives.FallbackDir

object DefaultImplicits {
  implicit val fb: FallbackDir = directives.fallbackTo500
}
