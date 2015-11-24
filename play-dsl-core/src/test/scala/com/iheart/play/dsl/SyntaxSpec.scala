package com.iheart.play.dsl

import org.specs2.concurrent.ExecutionEnv
import play.api.libs.json.{ JsString, JsValue, Json }
import play.api.mvc._
import Results._
import play.api.test.{ FakeRequest, PlaySpecification }
import Syntax._
import Extractor._
import shapeless._
import syntax.singleton._

import scala.concurrent.Future
object SyntaxSpec {
  case class RequestMessage(id: String, name: String, bar: Double)

  case class PartialRequestMessage(id: String, bar: Double)

  case class ResponseMessage(id: String, msg: String)

  class MyActor {
    def ask(any: Any): Future[Any] = any match {
      case RequestMessage(id, name, _) ⇒ Future.successful(ResponseMessage(id, "hello! " + name))
      case _                           ⇒ Future.successful("unrecognized")
    }
  }
  val actor = new MyActor()

  implicit val ab = new AskableBuilder[MyActor] {
    def apply(t: MyActor): Askable = t.ask
  }

  implicit val ff = Json.format[ResponseMessage]
  implicit val rff = Json.format[RequestMessage]
  implicit val pff = Json.format[PartialRequestMessage]

}

case class ProcessResult(content: Option[String])

class SyntaxSpec extends PlaySpecification {
  import SyntaxSpec._
  import com.iheart.play.dsl.DefaultImplicits._
  import directives._

  "end to end syntax" >> {

    "with extraction" >> {

      val controller = new Controller {
        val withExtraction = handle(
          from(req ⇒ 'name ->> req.headers("my_name") :: HNil),
          process[RequestMessage] using actor
            expectAny {
              case ResponseMessage(id, msg) ⇒ Ok(s"${id} ${msg}")
            }
        )
      }

      val req = FakeRequest().withHeaders("my_name" → "mike")

      val result: Future[Result] = call(controller.withExtraction("myId", 3.1), req)

      val bodyText: String = contentAsString(result)
      bodyText === "myId hello! mike"

    }

    "with extraction combination" >> {

      val controller = new Controller {
        val combined = handle(
          fromJson[PartialRequestMessage].body and from(req ⇒ 'name ->> req.headers("my_name") :: HNil),
          process[RequestMessage] using actor `then` expect[ResponseMessage].respondJson(Ok(_))
        )
      }

      val req = FakeRequest(POST, "/").withHeaders("my_name" → "mike").withJsonBody(Json.obj("id" → "myId", "bar" → 3.1))

      val result: Future[Result] = call(controller.combined(), req)

      val respBody: JsValue = contentAsJson(result)

      respBody === Json.obj("id" → JsString("myId"), "msg" → JsString("hello! mike"))
    }

    "without extraction" >> {
      val controller = new Controller {
        val withOutExtraction = handleParams(
          process[RequestMessage] using actor `then` expect[ResponseMessage].respondJson(Ok(_))
        )
      }

      val req = FakeRequest()

      val result: Future[Result] = call(controller.withOutExtraction("myId", "jon", 3.1), req)

      val respBody: JsValue = contentAsJson(result)
      respBody === Json.obj("id" → JsString("myId"), "msg" → JsString("hello! jon"))

    }

    "with filter " >> { implicit ev: ExecutionEnv ⇒
      val authentication: Filter[Any] = (req, result) ⇒ {
        req.headers.get("sessionId") match {
          case Some(sessionId) if sessionId.toInt > 0 ⇒ result
          case _                                      ⇒ Future.successful(Unauthorized("invalid session"))
        }
      }

      val withFilter = handleParams(
        process[RequestMessage] using actor `then` expect[ResponseMessage].respond(Ok) `with` authentication
      )
      val action = withFilter("myId", "jon", 3.1)

      val reqWithAuthInfo = FakeRequest().withHeaders("sessionId" → "3")

      val result1: Future[Result] = call(action, reqWithAuthInfo)

      result1.map(_.header.status) must be_==(OK).await

      val reqWithoutAuthInfo = FakeRequest()

      val result2: Future[Result] = call(action, reqWithoutAuthInfo)

      result2.map(_.header.status) must be_==(UNAUTHORIZED).await
    }

    "with AuthExtractor" >> { implicit ev: ExecutionEnv ⇒

      case class SessionInfo(sessionId: String)

      def sessionInfo(req: RequestHeader): Future[Either[String, SessionInfo]] = Future.successful(
        req.headers.get("sessionId") match {
          case Some(sid) ⇒ Right(SessionInfo(sid))
          case None      ⇒ Left("SessionId is missing from header")
        }
      )

      val handler = handle(
        fromAuthorized(sessionInfo)(si ⇒ 'id ->> si.sessionId :: HNil),
        process[RequestMessage] using actor `then` expect[ResponseMessage].respondJson(Ok(_))
      )

      val action = handler("mike", 3.4)

      val reqWithAuthInfo = FakeRequest().withHeaders("sessionId" → "3")

      val result1: Future[Result] = call(action, reqWithAuthInfo)

      result1.map(_.header.status) must be_==(OK).await

      (contentAsJson(result1) \ "id").as[String] === "3"

      val reqWithoutAuthInfo = FakeRequest()

      val result2: Future[Result] = call(action, reqWithoutAuthInfo)

      result2.map(_.header.status) must be_==(UNAUTHORIZED).await

    }

    "check field empty" >> {
      implicit val prf = Json.writes[ProcessResult]

      val dir: Directive[ProcessResult] = expect[ProcessResult].respondJson(Ok(_)).ifEmpty(_.content).respond(NotFound)

      "returns original result when field is filled" >> { implicit ev: ExecutionEnv ⇒
        val result = dir(FakeRequest().withBody(ProcessResult(Some("content"))))
        result.map(_.header.status) must be_==(OK).await
      }

      "returns alternative result when field is empty" >> { implicit ev: ExecutionEnv ⇒
        val result = dir(FakeRequest().withBody(ProcessResult(None)))
        result.map(_.header.status) must be_==(NOT_FOUND).await
      }
    }
  }
}
