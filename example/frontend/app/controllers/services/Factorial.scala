package controllers.services

import javax.inject.Inject

import _root_.util.ClusterAgentRepoProvider
import akka.util.Timeout
import api.FactorialService.Compute
import play.api.mvc._
import akka.pattern.ask
import play.api.libs.json._
import api.FactorialService
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

class Factorial @Inject() (repoProvider: ClusterAgentRepoProvider) extends Controller {
  val repo = repoProvider.get()
  implicit val to = Timeout(3.seconds)
  implicit val binIntFormat = new Format[BigInt] {
    def reads(json: JsValue): JsResult[BigInt] = json.validate[JsNumber].map(_.value.toBigInt())
    def writes(o: BigInt): JsValue = JsNumber(o.toLong)
  }
  implicit val format = Json.format[FactorialService.Result]
//  normal way
  def calcNormal(number: Int) = Action.async { _ ⇒
    repo.factorial ? Compute(number) map {
      case r: FactorialService.Result ⇒ Ok(Json.toJson(r))
      case _ ⇒ InternalServerError
    }
  }

// dsl way
  import asobu.dsl.Syntax._
  import asobu.dsl.akka.Builders._
  import asobu.dsl.DefaultImplicits._

  val calcDsl = handle(
    process[Compute].using(repo.factorial) >>
    expect[FactorialService.Result].respondJson(Ok)
  )
}
