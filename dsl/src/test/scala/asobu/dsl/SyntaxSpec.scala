package asobu.dsl

import asobu.dsl.Syntax._
import asobu.dsl.SyntaxFacilitators._
import asobu.dsl.extractors.HeaderExtractors
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.{After, Scope}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Results._
import play.api.mvc._
import play.api.test.{FakeRequest, PlaySpecification}
import shapeless._
import shapeless.syntax.singleton._
import scala.concurrent.Future
import scala.concurrent.duration._

object SyntaxSpec {
  case class RequestMsg(id: String, name: String, bar: Double)
  case class RequestMsgWithSession(sessionId: String, name: String, bar: Double)
  case object SpecialRequest

  case class PartialRequestMessage(id: String, bar: Double)

  case class ResponseMsg(id: String, msg: String, updated: DateTime = DateTime.now)

  class MyActor {
    def ask(any: Any): Future[Any] = any match {
      case RequestMsg(id, name, _)                   ⇒ Future.successful(ResponseMsg(id, "hello! " + name))
      case RequestMsgWithSession(sessionId, name, _) ⇒ Future.successful(ResponseMsg(sessionId, "hello! " + name))
      case SpecialRequest                            ⇒ Future.successful(ResponseMsg("-1", "special"))
      case _                                         ⇒ Future.successful("unrecognized")
    }
  }
  val actor = new MyActor()

  implicit val ab = new AskableBuilder[MyActor] {
    def apply(t: MyActor): Askable = t.ask
  }

  implicit val ff = Json.format[ResponseMsg]
  implicit val rff = Json.format[RequestMsg]
  implicit val rsff = Json.format[RequestMsgWithSession]
  implicit val pff = Json.format[PartialRequestMessage]

}

case class ProcessResult(content: Option[String])

class SyntaxSpec(implicit executionEnv: ExecutionEnv) extends PlaySpecification {
  import SyntaxSpec._
  import asobu.dsl.DefaultImplicits._
  val defaultTimeout = 10.seconds

  val authenticated: Filter[Any] = (req, result) ⇒ {
    req.headers.get("sessionId") match {
      case Some(sessionId) if sessionId.toInt > 0 ⇒ result
      case _                                      ⇒ Future.successful(Unauthorized("invalid session"))
    }
  }

  "end to end syntax" >> {

    "with normal extraction" >> new WithSystem {

      val controller = new Controller {
        val withExtraction = handle(
          fromFunc(name = (_: Request[AnyContent]).headers("my_name")),
          process[RequestMsg] using actor
            expectAny {
              case ResponseMsg(id, msg, _) ⇒ Ok(s"${id} ${msg}")
            }
        )
      }

      val req = FakeRequest().withHeaders("my_name" → "mike")

      val result: Future[Result] = call(controller.withExtraction("myId", 3.1), req)

      val bodyText: String = contentAsString(result)
      bodyText === "myId hello! mike"
    }

    "with body extraction" >> new WithSystem {

      val controller = new Controller {
        val combined = handle(
          fromJson[RequestMsg].body.allFields,
          process[RequestMsg] using actor next expect[ResponseMsg].respondJson(Ok(_))
        )
      }

      val req = FakeRequest(POST, "/").withJsonBody(Json.obj("id" → "myId", "bar" → 3.1, "name" → "mike"))

      val result: Future[Result] = call(controller.combined(), req)

      val respBody: JsValue = contentAsJson(result)

      (respBody \ "id").as[String] === "myId"
      (respBody \ "msg").as[String] === "hello! mike"
    }

    "with extraction combination" >> new WithSystem {
      import asobu.dsl.DefaultExtractorImplicits._
      import scala.concurrent.ExecutionContext.Implicits.global
      val controller = new Controller {
        val combined = handle(
          fromJson[PartialRequestMessage].body.allFields and from(name = HeaderExtractors.header[String]("my_name")),
          process[RequestMsg] using actor next expect[ResponseMsg].respondJson(Ok(_))
        )
      }

      val req = FakeRequest(POST, "/").withHeaders("my_name" → "mike").withJsonBody(Json.obj("id" → "myId", "bar" → 3.1))

      val result: Future[Result] = call(controller.combined(), req)

      val respBody: JsValue = contentAsJson(result)

      (respBody \ "id").as[String] === "myId"
      (respBody \ "msg").as[String] === "hello! mike"
    }

    "without extraction" >> new WithSystem {
      val controller = new Controller {
        val withOutExtraction = handle(
          process[RequestMsg] using actor next expect[ResponseMsg].respondJson(Ok(_))
        )
      }

      val req = FakeRequest()

      val result: Future[Result] = call(controller.withOutExtraction("myId", "jon", 3.1), req)

      val respBody: JsValue = contentAsJson(result)

      (respBody \ "id").as[String] === "myId"
      (respBody \ "msg").as[String] === "hello! jon"

    }

    case class SomeOtherMessage(boo: String)

    "failure when unexpected Message" >> new WithSystem { //implicit ev: ExecutionEnv ⇒
      val controller = new Controller {
        val withOutExtraction = handle(
          process[RequestMsg] using actor next expect[SomeOtherMessage].respond(Ok)
        )
      }

      val req = FakeRequest()

      val result: Future[Result] = call(controller.withOutExtraction("myId", "jon", 3.1), req)

      result.map(_.header.status) must be_==(INTERNAL_SERVER_ERROR).await

      val respBody: JsValue = contentAsJson(result)

      (respBody \ "error").as[String] === s"unexpected result ${classOf[ResponseMsg]} back"

    }

    "without any fields" >> new WithSystem {
      val controller = new Controller {
        val withOutFields = handle(
          process[SpecialRequest.type] using actor next expect[ResponseMsg].respondJson(Ok(_))
        )
      }

      val req = FakeRequest()

      val result: Future[Result] = call(controller.withOutFields(), req)

      val respBody: JsValue = contentAsJson(result)

      (respBody \ "id").as[String] === "-1"
      (respBody \ "msg").as[String] === "special"

    }

    "with filter " >> new WithSystem {

      val withFilter = handle(
        process[RequestMsg] using actor next expect[ResponseMsg].respond(Ok) `with` authenticated
      )
      val action = withFilter("myId", "jon", 3.1)

      val reqWithAuthInfo = FakeRequest().withHeaders("sessionId" → "3")

      val result1: Future[Result] = call(action, reqWithAuthInfo)

      result1.map(_.header.status) must be_==(OK).awaitFor(defaultTimeout)

      val reqWithoutAuthInfo = FakeRequest()

      val result2: Future[Result] = call(action, reqWithoutAuthInfo)

      result2.map(_.header.status) must be_==(UNAUTHORIZED).awaitFor(defaultTimeout)
    }

    case class SessionInfo(sessionId: String)

    def GetSessionInfo(req: RequestHeader): Future[Either[String, SessionInfo]] = Future.successful(
      req.headers.get("sessionId") match {
        case Some(sid) ⇒ Right(new SessionInfo(sid))
        case None      ⇒ Left("SessionId is missing from header")
      }
    )

    "with AuthExtractor" >> new WithSystem {
      val handler = handle(
        fromAuthorized(GetSessionInfo).from(id = (_: SessionInfo).sessionId),
        process[RequestMsg] using actor next expect[ResponseMsg].respondJson(Ok)
      )

      val action = handler("mike", 3.4)

      val reqWithAuthInfo = FakeRequest().withHeaders("sessionId" → "3")

      val result1: Future[Result] = call(action, reqWithAuthInfo)

      result1.map(_.header.status) must be_==(OK).awaitFor(defaultTimeout)

      (contentAsJson(result1) \ "id").as[String] === "3"

      val reqWithoutAuthInfo = FakeRequest()

      val result2: Future[Result] = call(action, reqWithoutAuthInfo)

      result2.map(_.header.status) must be_==(UNAUTHORIZED).awaitFor(defaultTimeout)

    }

    "check field empty" >> {
      implicit val prf = Json.writes[ProcessResult]

      val dir: Directive[ProcessResult] = expect[ProcessResult].respondJson(Ok(_)).ifEmpty(_.content).respond(NotFound)

      "returns original result when field is filled" >> { //implicit ev: ExecutionEnv ⇒
        val result = dir(FakeRequest().withBody(ProcessResult(Some("content"))))
        result.map(_.header.status) must be_==(OK).awaitFor(defaultTimeout)
      }

      "returns alternative result when field is empty" >> { //implicit ev: ExecutionEnv ⇒
        val result = dir(FakeRequest().withBody(ProcessResult(None)))
        result.map(_.header.status) must be_==(NOT_FOUND).awaitFor(defaultTimeout)
      }

    }

    "with filter" >> new WithSystem {
      import Filters._

      val endpoint = handle(
        authenticated {
          (process[RequestMsg] using actor) >> {
            expect[ResponseMsg] respondJson (Ok(_)) filter eTag(_.updated)
          }
        }
      )

      val result: Future[Result] = call(endpoint("myId", "mike", 3.5), FakeRequest().withHeaders("sessionId" → "2324"))

      result.map(_.header.headers.get(ETAG)) must beSome[String].awaitFor(defaultTimeout)

    }
  }

}
