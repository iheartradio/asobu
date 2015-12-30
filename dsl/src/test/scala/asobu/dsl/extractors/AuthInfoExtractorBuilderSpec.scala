package asobu.dsl.extractors

import asobu.dsl.{Extractor, ExtractResult, RequestExtractor}
import cats.data.Xor
import org.specs2.concurrent.ExecutionEnv
import play.api.mvc.RequestHeader
import play.api.test.{FakeRequest, PlaySpecification}
import Extractor._
import shapeless._, shapeless.record._
import scala.concurrent.Future
import asobu.dsl.CatsInstances._
import concurrent.ExecutionContext.Implicits.global

class AuthInfoExtractorBuilderSpec extends PlaySpecification {
  import asobu.dsl.DefaultImplicits._
  import AuthInfoExtractorBuilderSpec._
  "with AuthExtractor field" >> { implicit ev: ExecutionEnv ⇒
    val authExBuilder = new AuthInfoExtractorBuilder(GetSessionInfo)

    val extractor = compose(sessionId = authExBuilder.field('sessionId))

    val reqWithAuthInfo = FakeRequest().withHeaders("sessionId" → "3")

    extractor.run(reqWithAuthInfo).map(_('sessionId)).value must be_==(Xor.Right("3")).await

  }

}

object AuthInfoExtractorBuilderSpec {
  case class SessionInfo(sessionId: String)

  def GetSessionInfo(req: RequestHeader): Future[Either[String, SessionInfo]] = Future.successful(
    req.headers.get("sessionId") match {
      case Some(sid) ⇒ Right(new SessionInfo(sid))
      case None      ⇒ Left("SessionId is missing from header")
    }
  )
}
