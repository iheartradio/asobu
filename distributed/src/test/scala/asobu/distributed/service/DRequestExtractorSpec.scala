package asobu.distributed.service

import asobu.distributed.FakeRequests
import asobu.distributed.service.extractors.DRequestExtractor
import asobu.distributed.protocol.{RequestParams, DRequest}
import asobu.distributed.util.SerializableTest
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.libs.json.{JsNumber, Json}
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.core.routing.RouteParams
import shapeless._, record.Record
import asobu.dsl.CatsInstances._
import asobu.dsl.Extractor
import Extractor._
import extractors.DRequestExtractors._
import scala.concurrent.duration._

object DRequestExtractorSpec extends Specification with FakeRequests with SerializableTest {
  import asobu.dsl.DefaultExtractorImplicits._

  case class MyMessage(foo: String, bar: Int, bar2: Boolean)
  case class MyMessageBody(bar: Int)
  implicit val f = Json.format[MyMessageBody]

  "can build extractors without routesParams to extract" >> { implicit ev: ExecutionEnv ⇒

    val reqExtractor = compose(foo = header[String]("foo_h"), bar2 = header[Boolean]("bar2"))

    val bodyExtractor = BodyExtractors.json[MyMessageBody].allFields
    val extractor: DRequestExtractor[MyMessage] = DRequestExtractor.build[MyMessage](reqExtractor and bodyExtractor)

    val params = RequestParams.empty
    val req = request().withBody(rawJson(Json.obj("bar" → JsNumber(3)))).withHeaders("foo_h" → "foV", "bar2" → "true")

    val result = extractor.run(DRequest(params, req))
    result.toEither must beRight(MyMessage("foV", 3, true)).awaitFor(3.seconds)

  }

  "can build extractor correctly with query params to extract" >> { implicit ev: ExecutionEnv ⇒
    val reqExtractor = compose(foo = header[String]("foo_h"))
    val bodyExtractor = BodyExtractors.json[MyMessageBody].allFields
    val actionExtractor = DRequestExtractor.build[MyMessage](reqExtractor and bodyExtractor)

    val params = RequestParams(Map.empty, Map("bar2" → Seq("true")))
    val req = request().withBody(rawJson(Json.obj("bar" → JsNumber(3)))).withHeaders("foo_h" → "foV")

    val result = actionExtractor.run(DRequest(params, req))
    //    val expected = Record(bar2 = true, foo = "foV")
    result.toEither must beRight(MyMessage("foV", 3, true)).awaitFor(3.seconds)

  }

  "can build extractor correctly with path param to extract" >> { implicit ev: ExecutionEnv ⇒
    val reqExtractor = compose(foo = header[String]("foo_h"))
    val bodyExtractor = BodyExtractors.json[MyMessageBody].allFields
    val actionExtractor = DRequestExtractor.build[MyMessage](reqExtractor and bodyExtractor)

    val params = RequestParams(Map("bar2" → "true"), Map.empty)
    val req = request().withBody(rawJson(Json.obj("bar" → JsNumber(3)))).withHeaders("foo_h" → "foV")

    val result = actionExtractor.run(DRequest(params, req))
    //    val expected = Record(bar2 = true, foo = "foV")
    result.toEither must beRight(MyMessage("foV", 3, true)).awaitFor(3.seconds)

  }

  "can build extractor correctly without bodyExtractor" >> { implicit ev: ExecutionEnv ⇒
    val reqExtractor = compose(foo = header[String]("foo_h"))
    val actionExtractor = DRequestExtractor.build[MyMessage](reqExtractor)

    val params = RequestParams(Map.empty, Map("bar2" → Seq("true"), "bar" → Seq("3")))
    val req = request().withHeaders("foo_h" → "foV")

    val result = actionExtractor.run(DRequest(params, req))
    result.toEither must beRight(MyMessage("foV", 3, true)).awaitFor(3.seconds)

  }

  "can build extractor correctly without bodyExtractor and extra" >> { implicit ev: ExecutionEnv ⇒
    val actionExtractor = DRequestExtractor.build[MyMessage]()

    val params = RequestParams(Map.empty, Map("bar2" → Seq("true"), "bar" → Seq("3"), "foo" → Seq("foV")))

    val result = actionExtractor.run(DRequest(params, request()))
    val expected = MyMessage(foo = "foV", bar = 3, bar2 = true)

    result.toEither must beRight(expected).awaitFor(3.seconds)

  }
}
