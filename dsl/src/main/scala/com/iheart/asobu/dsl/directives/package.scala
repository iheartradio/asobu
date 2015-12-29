package asobu.dsl

import play.api.libs.json.{Writes, Json}

import scala.annotation.implicitNotFound
import scala.reflect._
import play.api.mvc._
import Results._
import Syntax._

package object directives {

  @implicitNotFound("You need to provide an implicit fall back directive to handle mismatches. You can use the default one by \n import play.akka.DefaultImplicits._ ")
  type FallbackDir = PartialDirective[Any]

  def fallbackTo500: FallbackDir = PartialDirective.synced[Any] {
    case e: Throwable ⇒ InternalServerError(Json.obj("error" → e.getMessage))
    case m            ⇒ InternalServerError(new MatchError(m).getMessage)
  }

}
