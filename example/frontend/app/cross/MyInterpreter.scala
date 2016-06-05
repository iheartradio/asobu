package cross

import javax.inject.{Inject, Singleton}

import api.authentication.Authenticated
import asobu.distributed.CustomRequestExtractorDefinition
import asobu.distributed.CustomRequestExtractorDefinition.Interpreter
import asobu.dsl.RequestExtractor
import asobu.dsl.extractors.AuthInfoExtractorBuilder
import play.api.mvc.RequestHeader

import scala.concurrent.{Future, ExecutionContext}
import com.google.inject.AbstractModule

@Singleton
class MyInterpreter @Inject() (implicit ec: ExecutionContext) extends Interpreter {

  def interpret[T](cred: CustomRequestExtractorDefinition[T]): RequestExtractor[T] = {
    cred match {
      case Authenticated =>
        new AuthInfoExtractorBuilder({ r: RequestHeader =>
          Future.successful(r.headers.get("UserId").toRight("Cannot find userId in header"))
        }).apply()
    }
  }
}

