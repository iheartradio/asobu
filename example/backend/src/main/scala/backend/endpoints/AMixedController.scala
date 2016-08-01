package backend.endpoints

import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import api.FactorialService
import api.FactorialService.Compute
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.service.Action.DistributedResult
import asobu.distributed.service.{BodyExtractor, EndpointsRegistryClient, DistributedController, Controller}
import backend.models.Student
import backend.school.StudentService
import backend.school.StudentService.Grade
import cats.data.Kleisli
import play.api.libs.json.{Format, Json}
import play.api.mvc.Results._
import concurrent.duration._
import api.authentication.Authenticated
import akka.stream.Materializer

//todo: this example includes endpoints irrelevant to each other. Improve by breaking it up.
case class AMixedController(factorialBackend: ActorRef, studentBackend: ActorRef)(implicit mat: Materializer) extends DistributedController {
  import Formats._

  import concurrent.ExecutionContext.Implicits.global
  import asobu.dsl.DefaultExtractorImplicits._

  implicit val ao : Timeout = 30.seconds

  val actions = List(
    handle("ep1",
      process[Compute]())(
        using(factorialBackend).
          expect[FactorialService.Result] >>
          respond(r => Ok(r.result.toString))
      ),

    handle("ep3",
      process[Student](
        Authenticated.void,
        fromJsonBody[Student]
      ))(
        using(studentBackend).
          expect[StudentService.Grade] >>
          respondJson(Ok)
      )
  )
}


object Formats {
  implicit val studentFormat: Format[Student] = Json.format[Student]
  implicit val gradeFormat: Format[Grade] = Json.format[Grade]
}

