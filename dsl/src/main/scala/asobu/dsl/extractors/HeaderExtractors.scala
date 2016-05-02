package asobu.dsl.extractors

import asobu.dsl.util.Read
import asobu.dsl.{RequestExtractor, ExtractResult}
import cats.data.Kleisli
import play.api.mvc.{Request, AnyContent}

import ExtractResult._
import shapeless.Witness
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

trait HeaderExtractors {
  def header[T: Read](key: String)(implicit fbr: FallbackResult, ex: ExecutionContext): RequestExtractor[T] = Kleisli({ (req: Request[AnyContent]) ⇒
    val parsed: Try[T] = for {
      v ← req.headers.get(key).fold[Try[String]](Failure[String](new NoSuchElementException(s"Cannot find $key in header")))(Success(_))
      r ← Read[T].parse(v)
    } yield r

    fromTry(parsed)
  })

}

object HeaderExtractors extends HeaderExtractors

