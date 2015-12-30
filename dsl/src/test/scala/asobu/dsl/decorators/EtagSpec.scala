package asobu.dsl.decorators

import asobu.dsl.Extractor
import org.joda.time.DateTime
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.mutable.ExecutionEnvironment
import play.api.mvc.Result
import play.api.mvc.Results.{Ok, NotModified}
import play.api.http.Status._
import concurrent.duration._
import play.api.http.HeaderNames.ETAG
import cats.std.future._

import scala.concurrent.Future

class EtagSpec extends Specification with ExecutionEnvironment {
  import EtagSpec._

  def is(implicit ee: ExecutionEnv) = {
    val time = new DateTime(2014, 12, 1, 12, 45)
    val extractor = Etag(
      Extractor((a: A) ⇒ B(a.foo, time)),
      Extractor((b: B) ⇒ Ok(b.foo))
    )((_: B).time)

    "Returns NotModified when etag matches " >> {
      val result = extractor.run((List(ETAG → time.getMillis.toString), A("Blah")))
      result.toEither must beLeft(NotModified).awaitFor(10.seconds)
    }

    "Returns original Result when etag doesn't exist " >> {
      val result = extractor.run((Nil, A("Blah")))
      result.toEither must beRight[Result].awaitFor(10.seconds)

      val resultF: Future[Result] = result.getOrElse(throw new Exception())

      resultF.map(_.header.status) must be_==(OK).awaitFor(10.seconds)
      resultF.map(_.header.headers(ETAG)) must be_==(time.getMillis.toString).awaitFor(10.seconds)
    }

    "Returns original Result when etag exist but not match " >> {
      val result = extractor.run((List(ETAG → DateTime.now.getMillis.toString), A("Blah")))
      result.toEither must beRight[Result].awaitFor(10.seconds)

      val resultF: Future[Result] = result.getOrElse(throw new Exception())

      resultF.map(_.header.status) must be_==(OK).awaitFor(10.seconds)
      resultF.map(_.header.headers(ETAG)) must be_==(time.getMillis.toString).awaitFor(10.seconds)
    }
  }
}

object EtagSpec {
  case class A(foo: String)
  case class B(foo: String, time: DateTime)

}
