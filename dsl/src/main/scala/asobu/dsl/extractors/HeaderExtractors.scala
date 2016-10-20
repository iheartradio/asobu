package asobu.dsl.extractors

import asobu.dsl.util.Read
import asobu.dsl._
import RequestExtractor._
import cats.data.Kleisli
import play.api.mvc.{Request, AnyContent}

import ExtractResult._
import shapeless.Witness
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import CatsInstances._

trait HeaderExtractors {
  import HeaderExtractors.missingHeaderException
  def header[T: Read](key: String)(implicit fbr: FallbackResult, ex: ExecutionContext): RequestExtractor[T] = {
    RequestExtractor(_.headers.get(key)) andThen
      PrimitiveExtractors.stringOption(missingHeaderException(key))
  }
}

object HeaderExtractors extends HeaderExtractors {
  def missingHeaderException(key: String): Throwable = new NoSuchElementException(s"Cannot find $key in header")
}

