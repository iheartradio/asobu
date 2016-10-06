package asobu.distributed.gateway.enricher

import javax.inject.Inject

import asobu.distributed.DRequest
import asobu.distributed.RequestEnricherDefinition
import asobu.distributed.RequestEnricherDefinition.{OrElse, AndThen}
import asobu.distributed.gateway._
import asobu.dsl.Extractor._
import asobu.dsl.{ExtractResult, Extractor, RequestExtractor}
import play.api.mvc.{Request, AnyContent, Results}, Results.InternalServerError

import scala.concurrent.ExecutionContext
import scala.reflect._

trait Interpreter[T <: Def] {
  def apply(enricherDef: T)(implicit exec: ExecutionContext): RequestEnricher
}

object Interpreter {

  case class UnknownEnrichDefinition(defName: String) extends Exception(s"Support for EnricherDefinition $defName is not implemented yet.")

  def interpret[T <: Def: ClassTag](enricherDefinition: RequestEnricherDefinition)(
    implicit
    interpreter: Interpreter[T],
    ec: ExecutionContext
  ): RequestEnricher = {
    import asobu.dsl.CatsInstances._
    enricherDefinition match {
      case t: T if classTag[T].runtimeClass.isInstance(t) ⇒ interpreter(t)
      case AndThen(a, b)                                  ⇒ interpret(a) andThen interpret(b)
      case OrElse(a, b)                                   ⇒ interpret(a) orElse interpret(b)
      case unknown                                        ⇒ throw new UnknownEnrichDefinition(unknown.getClass.getCanonicalName)
    }
  }

}

class DisabledInterpreter(implicit ex: ExecutionContext) extends Interpreter[Nothing] {
  def apply(ed: Nothing)(implicit exec: ExecutionContext): RequestEnricher = null
}
