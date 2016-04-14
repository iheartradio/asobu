package asobu.distributed.service

import asobu.distributed.service.ActionExtractor.RouteParamsExtractor
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import play.core.routing.RouteParams
import shapeless._
import shapeless.record._
import asobu.dsl.CatsInstances._
import concurrent.ExecutionContext.Implicits.global

class RouteParamsExtractorSpec extends Specification {

  "generates from Record T" >> {
    type Rec = Record.`'x -> Int, 'y -> String, 'z -> Boolean`.T

    val rpe = RouteParamsExtractor[Rec]
    val result = rpe.run(RouteParams(Map("x" → Right("3")), Map.empty))
    result.isLeft must beTrue

    val result2 = rpe.run(RouteParams(Map("x" → Right("3"), "y" → Right("a"), "z" → Right("true")), Map.empty))
    result2.toEither must beRight(Record(x = 3, y = "a", z = true))
  }

  "generates from record with a single field" >> {
    type Rec = Record.`'z -> Boolean`.T

    val rpe = RouteParamsExtractor[Rec]
    val result = rpe.run(RouteParams(Map("z" → Right("true")), Map.empty))
    result.toEither must beRight(Record(z = true))

  }

  "generates empty from HNil" >> {
    val rpe = RouteParamsExtractor[HNil]
    val result = rpe.run(RouteParams(Map("x" → Right("3")), Map.empty))
    result.toEither must beRight(HNil)
  }

}
