package asobu.dsl

import org.specs2.concurrent.ExecutionEnv
import play.api.libs.json.Json
import play.api.mvc.{Request, AnyContent}
import play.api.test.{FakeRequest, PlaySpecification}
import shapeless._
import syntax.singleton._
import Syntax._
import concurrent.duration._
case class Foo(bar: String, bar2: Int)

class ExtractorSpec extends PlaySpecification {
  implicit val ffoo = Json.format[Foo]
  import ExtractorOps._

  "jsonBodyExtractor creation" >> { implicit ee: ExecutionEnv ⇒

    val extractor = fromJson[Foo].body.allFields

    val result = extractor.run(FakeRequest().withJsonBody(Json.obj("bar" → "hello", "bar2" → 3))).value

    result.map(_.getOrElse(HNil)) must be_==("bar" ->> "hello" :: "bar2" ->> 3 :: HNil).awaitFor(10.seconds)

  }

  "and combines two extractor" >> { implicit ee: ExecutionEnv ⇒
    val extractor1 = fromJson[Foo].body.allFields

    val w = Witness("bar3")

    val extractor2 = Extractor.composeF(bar3 = (_: Request[AnyContent]).headers("bar3"))

    val extractor = extractor1 and extractor2

    val result = extractor.run(FakeRequest().withJsonBody(Json.obj("bar" → "hello", "bar2" → 3)).withHeaders("bar3" → "bar3Value")).value

    result.map(_.getOrElse(HNil)) must be_==('bar ->> "hello" :: 'bar2 ->> 3 :: 'bar3 ->> "bar3Value" :: HNil).awaitFor(10.seconds)

  }

  "and combines compose two extractor" >> { implicit ee: ExecutionEnv ⇒

    val extractor1 = fromJson[Foo].body.allFields

    val extractor2 = Extractor.compose(bar3 = RequestExtractor(_.headers("bar3")))

    val extractor = extractor1 and extractor2

    val result = extractor.run(FakeRequest().withJsonBody(Json.obj("bar" → "hello", "bar2" → 3)).withHeaders("bar3" → "bar3Value")).value

    result.map(_.getOrElse(HNil)) must be_==('bar ->> "hello" :: 'bar2 ->> 3 :: 'bar3 ->> "bar3Value" :: HNil).awaitFor(10.seconds)

  }
}
