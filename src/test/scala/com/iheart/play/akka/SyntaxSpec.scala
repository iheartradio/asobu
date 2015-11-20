package com.iheart.play.akka

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

class SyntaxSpec extends PlaySpecification {
  import SyntaxSpec._
  import com.iheart.play.akka.DefaultImplicits._
  import directives._

  "end to end syntax" >> {

    "with extraction" >> {

      val controller = new Controller {
        val withExtraction = handle(
          from(req ⇒ 'name ->> req.headers("my_name") :: HNil),
          process[RequestMessage] using actor
            respondWith {
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
          fromJson[PartialRequestMessage].body |+| from(req ⇒ 'name ->> req.headers("my_name") :: HNil),
          (process[RequestMessage] using actor) >> simpleOk[ResponseMessage]
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
          (process[RequestMessage] using actor) >> simpleOk[ResponseMessage]
        )
      }

      val req = FakeRequest()

      val result: Future[Result] = call(controller.withOutExtraction("myId", "jon", 3.1), req)

      val respBody: JsValue = contentAsJson(result)
      respBody === Json.obj("id" → JsString("myId"), "msg" → JsString("hello! jon"))

    }

  }
}
