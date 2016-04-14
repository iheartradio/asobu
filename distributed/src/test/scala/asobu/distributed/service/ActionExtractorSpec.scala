package asobu.distributed.service

import asobu.distributed.RequestExtractorDefinition
import asobu.distributed.util.SerializableTest
import asobu.dsl.{Extractor, ExtractResult}
import asobu.dsl.ExtractorOps._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.libs.json.{JsNumber, Json}
import play.api.mvc.{AnyContent, Request}
import play.api.test.FakeRequest
import play.core.routing.RouteParams
import shapeless._, record.Record
import asobu.dsl.CatsInstances._

object ActionExtractorSpec extends Specification with SerializableTest {
  import RequestExtractorDefinition._
  import asobu.dsl.DefaultExtractorImplicits._

  case class MyMessage(foo: String, bar: Int, bar2: Boolean)
  case class MyMessageBody(bar: Int)
  implicit val f = Json.format[MyMessageBody]

  "can build extractors without routesParams to extract" >> { implicit ev: ExecutionEnv ⇒

    val reqExtractor = compose(foo = header[String]("foo_h"), bar2 = header[Boolean]("bar2"))
    val bodyExtractor = BodyExtractor.json[MyMessageBody].allFields
    val extractors = ActionExtractor.build[MyMessage](reqExtractor, bodyExtractor)

    val params = RouteParams(Map.empty, Map.empty)
    val req: Request[AnyContent] = FakeRequest().withJsonBody(Json.obj("bar" → JsNumber(3))).withHeaders("foo_h" → "foV", "bar2" → "true")

    val result = extractors.remoteExtractorDef.extractor.run((params, req))
    val expected = Record(foo = "foV", bar2 = true)
    result.toEither must beRight(expected).await

  }

  "can build extractor correctly with routesParams to extract" >> { implicit ev: ExecutionEnv ⇒
    val reqExtractor = compose(foo = header[String]("foo_h"))
    val bodyExtractor = BodyExtractor.json[MyMessageBody].allFields
    val extractors = ActionExtractor.build[MyMessage](reqExtractor, bodyExtractor)

    val params = RouteParams(Map.empty, Map("bar2" → Seq("true")))
    val req: Request[AnyContent] = FakeRequest().withJsonBody(Json.obj("bar" → JsNumber(3))).withHeaders("foo_h" → "foV")

    val result = extractors.remoteExtractorDef.extractor.run((params, req))
    val expected = Record(bar2 = true, foo = "foV")
    result.toEither must beRight(expected).await

  }

  "can build extractor correctly without bodyExtractor" >> { implicit ev: ExecutionEnv ⇒
    val reqExtractor = compose(foo = header[String]("foo_h"))
    val bodyExtractor = BodyExtractor.empty
    val extractors = ActionExtractor.build[MyMessage](reqExtractor, bodyExtractor)

    val params = RouteParams(Map.empty, Map("bar2" → Seq("true"), "bar" → Seq("3")))
    val req: Request[AnyContent] = FakeRequest().withHeaders("foo_h" → "foV")

    val result = extractors.remoteExtractorDef.extractor.run((params, req))
    val expected = Record(bar = 3, bar2 = true, foo = "foV")
    result.toEither must beRight(expected).await

  }

  "can build extractor correctly without bodyExtractor and extra" >> { implicit ev: ExecutionEnv ⇒
    val extractors = ActionExtractor.build[MyMessage](RequestExtractorDefinition.empty, BodyExtractor.empty)

    val params = RouteParams(Map.empty, Map("bar2" → Seq("true"), "bar" → Seq("3"), "foo" → Seq("foV")))

    val result = extractors.remoteExtractorDef.extractor.run((params, FakeRequest()))
    val expected = Record(foo = "foV", bar = 3, bar2 = true)

    result.toEither must beRight(expected).await

  }

  "remote extractor should be serializable" >> {
    import java.io.{ByteArrayOutputStream, ObjectOutputStream}

    import asobu.dsl.DefaultExtractorImplicits._

    val reqExtractor = compose(foo = header[String]("foo_h"))
    val bodyExtractor = BodyExtractor.empty

    val remoteExtractorDef =
      ActionExtractor.build[MyMessage](reqExtractor, bodyExtractor).remoteExtractorDef

    isSerializable(remoteExtractorDef) must beTrue
  }
}
