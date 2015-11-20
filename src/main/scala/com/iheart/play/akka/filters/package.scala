package com.iheart.play.akka

import play.api.mvc.Results._

import scala.concurrent.Future

package object filters {

  def notFoundIfEmpty[RMT, OT](
    fieldExtractor:  RMT ⇒ Option[OT],
    notFoundMessage: Option[String]   = None
  ): Filter[RMT] = (req, result) ⇒ {

    val notFoundResult = Future.successful(NotFound(notFoundMessage.getOrElse("")))
    fieldExtractor(req.body).fold(notFoundResult)(_ ⇒ result)
  }

}
