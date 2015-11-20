package com.iheart.play.akka

import play.api.libs.json.{ Writes, Json }

import scala.annotation.implicitNotFound
import scala.reflect._
import play.api.mvc._
import Results._
import Syntax._

package object directives {

  @implicitNotFound("You need to provide an implicit fall back directive to handle mismatches. You can use the default one by \n import com.iheart.play.akka.DefaultImplicits._ ")
  type FallBackDir = Directive[Any]

  def fallBackTo500: FallBackDir = Directive.synced[Any] {
    case e: Throwable ⇒ InternalServerError(e.getMessage)
    case m            ⇒ InternalServerError(new MatchError(m).getMessage)
  }

  def simpleOk[T: ClassTag: Writes](implicit fb: FallBackDir): Directive[Any] =
    Directive((t: T) ⇒ Ok(Json.toJson(t))).fallback(fb)

  def checkType[T: ClassTag](result: Result)(implicit fb: FallBackDir): Directive[Any] =
    Directive.constant[T](result).fallback(fb)

}
