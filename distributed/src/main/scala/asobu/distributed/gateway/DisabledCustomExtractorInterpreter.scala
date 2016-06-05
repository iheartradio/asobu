package asobu.distributed.gateway

import javax.inject.Inject

import asobu.distributed.CustomRequestExtractorDefinition
import asobu.distributed.CustomRequestExtractorDefinition.Interpreter
import asobu.dsl.{Extractor, RequestExtractor}
import cats.data.XorT
import cats.std.future._
import com.google.inject.Singleton
import play.api.mvc.{Request, AnyContent, Results}, Results.InternalServerError

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DisabledCustomExtractorInterpreter @Inject() (implicit ec: ExecutionContext) extends Interpreter {

  def interpret[T](cred: CustomRequestExtractorDefinition[T]): RequestExtractor[T] = {
    val message = s"Trying to use $cred but custom extractor is disabled. To enabled it, implement `asobu.distributed.CustomRequestExtractorDefinition.Interpreter` and set the config `asobu.${CustomRequestExtractorDefinition.interpreterClassConfigKey}`"

    Extractor.fromFunction((_: Request[AnyContent]) ⇒ XorT.left(Future.successful(InternalServerError(message))))
  }
}
