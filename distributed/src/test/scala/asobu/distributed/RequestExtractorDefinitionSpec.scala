package asobu.distributed

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.api.test.FakeRequest
import asobu.dsl.CatsInstances._

import shapeless._
import shapeless.record.Record

class RequestExtractorDefinitionSpec extends Specification {
  import RequestExtractorDefinition._
  "can compose" >> { implicit ev: ExecutionEnv ⇒
    import asobu.dsl.DefaultExtractorImplicits._
    val re = compose(a = (header[String]("big"): RequestExtractorDefinition[String]))
    val extractor = re.apply(ev.executionContext)

    val result = extractor.run(FakeRequest().withHeaders("big" → "lala"))

    result.toEither must beRight(Record(a = "lala")).await

  }

}
