package asobu.dsl

import scala.reflect.ClassTag
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import play.api.cache.CacheApi
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc._
import Results._
import play.api.test.{FakeRequest, PlaySpecification}
import Syntax._
import SyntaxFacilitators._
import Extractor._
import shapeless._
import syntax.singleton._

import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.http.HeaderNames._

object SyntaxSpec {
  case class RequestMsg(id: String, name: String, bar: Double)
  case object SpecialRequest

  case class PartialRequestMessage(id: String, bar: Double)

  case class ResponseMsg(id: String, msg: String, updated: DateTime = DateTime.now)

  class MyActor {
    def ask(any: Any): Future[Any] = any match {
      case RequestMsg(id, name, _) ⇒ Future.successful(ResponseMsg(id, "hello! " + name))
      case SpecialRequest          ⇒ Future.successful(ResponseMsg("-1", "special"))
      case _                       ⇒ Future.successful("unrecognized")
    }
  }
  val actor = new MyActor()

  implicit val ab = new AskableBuilder[MyActor] {
    def apply(t: MyActor): Askable = t.ask
  }

  implicit val ff = Json.format[ResponseMsg]
  implicit val rff = Json.format[RequestMsg]
  implicit val pff = Json.format[PartialRequestMessage]

}

case class ProcessResult(content: Option[String])

class SyntaxSpec extends PlaySpecification {
  import SyntaxSpec._
  import asobu.dsl.DefaultImplicits._
  import directives._

  val authenticated: Filter[Any] = (req, result) ⇒ {
    req.headers.get("sessionId") match {
      case Some(sessionId) if sessionId.toInt > 0 ⇒ result
      case _                                      ⇒ Future.successful(Unauthorized("invalid session"))
    }
  }

  "end to end syntax" >> {

    "with normal extraction" >> {

      val controller = new Controller {
        val withExtraction = handle(
          from(req ⇒ 'name ->> req.headers("my_name") :: HNil),
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

    "with body extraction" >> {

      val controller = new Controller {
        val combined = handle(
          fromJson[RequestMsg].body,
          process[RequestMsg] using actor next expect[ResponseMsg].respondJson(Ok(_))
        )
      }

      val req = FakeRequest(POST, "/").withJsonBody(Json.obj("id" → "myId", "bar" → 3.1, "name" → "mike"))

      val result: Future[Result] = call(controller.combined(), req)

      val respBody: JsValue = contentAsJson(result)

      (respBody \ "id").as[String] === "myId"
      (respBody \ "msg").as[String] === "hello! mike"
    }

    "with extraction combination" >> {

      val controller = new Controller {
        val combined = handle(
          fromJson[PartialRequestMessage].body and from(req ⇒ 'name ->> req.headers("my_name") :: HNil),
          process[RequestMsg] using actor next expect[ResponseMsg].respondJson(Ok(_))
        )
      }

      val req = FakeRequest(POST, "/").withHeaders("my_name" → "mike").withJsonBody(Json.obj("id" → "myId", "bar" → 3.1))

      val result: Future[Result] = call(controller.combined(), req)

      val respBody: JsValue = contentAsJson(result)

      (respBody \ "id").as[String] === "myId"
      (respBody \ "msg").as[String] === "hello! mike"
    }

    "without extraction" >> {
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

    "without any fields" >> {
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

    "with filter " >> { implicit ev: ExecutionEnv ⇒

      val withFilter = handle(
        process[RequestMsg] using actor next expect[ResponseMsg].respond(Ok) `with` authenticated
      )
      val action = withFilter("myId", "jon", 3.1)

      val reqWithAuthInfo = FakeRequest().withHeaders("sessionId" → "3")

      val result1: Future[Result] = call(action, reqWithAuthInfo)

      result1.map(_.header.status) must be_==(OK).await

      val reqWithoutAuthInfo = FakeRequest()

      val result2: Future[Result] = call(action, reqWithoutAuthInfo)

      result2.map(_.header.status) must be_==(UNAUTHORIZED).await
    }

    case class SessionInfo(sessionId: String)

    "with AuthExtractor" >> { implicit ev: ExecutionEnv ⇒

      def SessionInfo(req: RequestHeader): Future[Either[String, SessionInfo]] = Future.successful(
        req.headers.get("sessionId") match {
          case Some(sid) ⇒ Right(new SessionInfo(sid))
          case None      ⇒ Left("SessionId is missing from header")
        }
      )

      val handler = handle(
        fromAuthorized(SessionInfo)(si ⇒ 'id ->> si.sessionId :: HNil),
        process[RequestMsg] using actor next expect[ResponseMsg].respondJson(Ok)
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

    "with multiple filters" >> { implicit ev: ExecutionEnv ⇒
      import Filters._

      implicit val cacheApi = new CacheApi {
        def set(key: String, value: Any, expiration: Duration): Unit = ???
        def get[T: ClassTag](key: String): Option[T] = ???
        def getOrElse[A: ClassTag](key: String, expiration: Duration)(orElse: ⇒ A): A = orElse
        def remove(key: String): Unit = ???
      }

      val endpoint = handle(
        (cached(3.hours) and authenticated) {
          (process[RequestMsg] using actor) >> {
            expect[ResponseMsg] respondJson (Ok(_)) filter eTag(_.updated)
          }
        }
      )

      val result: Future[Result] = call(endpoint("myId", "mike", 3.5), FakeRequest().withHeaders("sessionId" → "2324"))

      result.map(_.header.headers.get(ETAG)) must beSome[String].await

    }
  }

}
