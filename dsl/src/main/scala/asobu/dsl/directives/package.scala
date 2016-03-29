package asobu.dsl

import play.api.libs.json.{Writes, Json}

import scala.annotation.implicitNotFound
import scala.reflect._
import play.api.mvc._
import Results._
import Syntax._

package object directives {

  @implicitNotFound("You need to provide an implicit fall back directive to handle mismatches. You can use the default one by \n import asobu.dsl.DefaultImplicits._ ")
  type FallbackDir = PartialDirective[Any]

  def fallbackTo500: FallbackDir = PartialDirective.synced[Any] {
    case e: Throwable ⇒ errorOf(e.toString)
    case m            ⇒ errorOf(s"unexpected result ${m.getClass} back")
  }

  private def errorOf(msg: String): Result = InternalServerError(Json.obj("error" → msg))

}
