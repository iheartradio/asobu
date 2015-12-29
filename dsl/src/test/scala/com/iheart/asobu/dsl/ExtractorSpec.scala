package asobu.dsl

import org.specs2.concurrent.ExecutionEnv
import play.api.libs.json.{Json, Format}
import play.api.mvc.{Request, AnyContent}
import play.api.test.{FakeRequest, PlaySpecification}
import shapeless._
import syntax.singleton._
import Syntax._
import Extractor._
case class Foo(bar: String, bar2: Int)

class ExtractorSpec extends PlaySpecification {
  implicit val ffoo = Json.format[Foo]

  "jsonBodyExtractor creation" >> { implicit ee: ExecutionEnv ⇒

    val extractor = fromJson[Foo].body

    val result = extractor(FakeRequest().withJsonBody(Json.obj("bar" → "hello", "bar2" → 3)))

    result.map(_.getOrElse(HNil)) must be_==("bar" ->> "hello" :: "bar2" ->> 3 :: HNil).await

  }

  "and combines two extractor" >> { implicit ee: ExecutionEnv ⇒

    val extractor1 = fromJson[Foo].body

    val extractor2 = Extractor((req: Request[AnyContent]) ⇒ 'bar3 ->> req.headers("bar3") :: HNil)

    val extractor = extractor1 and extractor2

    val result = extractor(FakeRequest().withJsonBody(Json.obj("bar" → "hello", "bar2" → 3)).withHeaders("bar3" → "bar3Value"))

    result.map(_.getOrElse(HNil)) must be_==('bar ->> "hello" :: 'bar2 ->> 3 :: 'bar3 ->> "bar3Value" :: HNil).await

  }
}
