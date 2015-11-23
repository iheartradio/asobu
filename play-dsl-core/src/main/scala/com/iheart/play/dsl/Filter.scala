package com.iheart.play.dsl

import cats._
import syntax.all._
import play.api.mvc.Results._

import scala.concurrent.Future

object Filter {

  implicit def filterMonoid[RMT] = new Monoid[Filter[RMT]] {
    def empty: Filter[RMT] = (_, result) ⇒ result

    def combine(x: Filter[RMT], y: Filter[RMT]): Filter[RMT] = { (req, result) ⇒
      y(req, x(req, result))
    }
  }
}

