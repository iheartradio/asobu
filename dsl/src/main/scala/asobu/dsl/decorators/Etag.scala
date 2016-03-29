package asobu.dsl.decorators

import asobu.dsl._
import org.joda.time.DateTime
import play.api.mvc.Result
import play.api.mvc.Results._
import cats.std.all._
import scala.concurrent.{ExecutionContext, Future}

object Etag {
  import Extractor._
  type Headers = Seq[(String, String)]
  def apply[A, B](
    extractor1: Extractor[A, B],
    extractor2: Extractor[B, Result]
  )(getETag: B ⇒ DateTime)(implicit ec: ExecutionContext): Extractor[(Headers, A), Result] = { (p: (Headers, A)) ⇒
    import play.api.http.HeaderNames.ETAG
    val (headers, a) = p
    val etagInHead: Option[Long] = headers.toMap.get(ETAG).map(_.toLong)
    val resultB: ExtractResult[B] = extractor1.run(a)
    for {
      b ← resultB
      etagInData = getETag(b).getMillis
      _ ← resultB.ensure(NotModified)(_ ⇒ etagInHead.fold(true)(_ != etagInData))
      r ← extractor2.run(b)
    } yield r.withHeaders(ETAG → etagInData.toString)
  }

}
