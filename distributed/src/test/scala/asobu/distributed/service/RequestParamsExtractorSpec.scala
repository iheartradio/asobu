package asobu.distributed.service

import asobu.distributed.protocol.RequestParams
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import shapeless._
import shapeless.record._
import asobu.dsl.CatsInstances._
import concurrent.ExecutionContext.Implicits.global

class RequestParamsExtractorSpec extends Specification {

  "generates from Record T" >> { implicit ev: ExecutionEnv ⇒
    type Rec = Record.`'x -> Int, 'y -> String, 'z -> Boolean`.T

    val rpe = RequestParamsExtractor[Rec]
    val result = rpe.run(RequestParams(Map("x" → "3"), Map.empty))
    result.isLeft must beTrue.await

    val result2 = rpe.run(RequestParams(Map("x" → "3", "y" → "a", "z" → "true"), Map.empty))
    result2.toEither must beRight(Record(x = 3, y = "a", z = true)).await
  }

  "generates from record with a single field" >> { implicit ev: ExecutionEnv ⇒
    type Rec = Record.`'z -> Boolean`.T

    val rpe = RequestParamsExtractor[Rec]
    val result = rpe.run(RequestParams(Map("z" → "true"), Map.empty))
    result.toEither must beRight(Record(z = true)).await

  }

  "generates empty from HNil" >> { implicit ev: ExecutionEnv ⇒
    val rpe = RequestParamsExtractor[HNil]
    val result = rpe.run(RequestParams(Map("x" → "3"), Map.empty))
    result.toEither must beRight(HNil: HNil).await
  }

}
