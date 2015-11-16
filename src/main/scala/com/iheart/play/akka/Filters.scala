package com.iheart.play.akka

import play.api.mvc.Results._

import scala.concurrent.Future

object Filters {
  def notFoundIfEmpty[RMT, OT](
    extractor:       RMT ⇒ Option[OT],
    notFoundMessage: Option[String]   = None
  ): Filter[RMT] = (req, result) ⇒ {

    val notFoundResult = Future.successful(NotFound(notFoundMessage.getOrElse("")))
    extractor(req.body).fold(notFoundResult)(_ ⇒ result)
  }

}
