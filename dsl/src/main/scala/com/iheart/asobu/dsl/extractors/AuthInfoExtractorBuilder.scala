package asobu.dsl.extractors

import cats.data.Xor
import asobu.dsl.Extractor
import play.api.mvc.RequestHeader
import play.api.mvc.Results._
import shapeless.HList

import scala.concurrent.Future

class AuthInfoExtractorBuilder[AuthInfoT](buildAuthInfo: RequestHeader ⇒ Future[Either[String, AuthInfoT]]) {
  import scala.concurrent.ExecutionContext.Implicits.global

  def apply[Repr <: HList](toRecord: AuthInfoT ⇒ Repr): Extractor[Repr] = { req ⇒
    buildAuthInfo(req).map {
      case Left(msg)       ⇒ Xor.Left(Unauthorized(msg))
      case Right(authInfo) ⇒ Xor.Right(toRecord(authInfo))
    }
  }
}
