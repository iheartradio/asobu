package com.iheart.play.dsl

import play.api.mvc.Results._
import play.api.cache.CacheApi

import scala.concurrent.Future
import scala.concurrent.duration.Duration

package object filters {

  def notFoundIfEmpty[RMT, OT](
    fieldExtractor:  RMT ⇒ Option[OT],
    notFoundMessage: Option[String]   = None
  ): Filter[RMT] = (req, result) ⇒ {

    val notFoundResult = Future.successful(NotFound(notFoundMessage.getOrElse("")))
    fieldExtractor(req.body).fold(notFoundResult)(_ ⇒ result)
  }

  def caching[RMT](duration: Duration)(implicit cache: CacheApi): Filter[RMT] =
    (req, result) ⇒ cache.getOrElse(req.body.toString)(result)

}
