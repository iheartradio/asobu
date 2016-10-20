package asobu.dsl.extractors

import asobu.dsl.{ExtractResult, FallbackResult, Extractor}
import Extractor._
import ExtractResult._
import asobu.dsl.util.Read
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure, Try}

trait PrimitiveExtractors {
  def stringOption[T: Read](ifEmpty: ⇒ Throwable)(implicit fbr: FallbackResult, ex: ExecutionContext): Extractor[Option[String], T] = (strO: Option[String]) ⇒ {
    val parsed: Try[T] = for {
      v ← strO.fold[Try[String]](Failure[String](ifEmpty))(Success(_))
      r ← Read[T].parse(v)
    } yield r

    fromTry(parsed)
  }
}

object PrimitiveExtractors extends PrimitiveExtractors
