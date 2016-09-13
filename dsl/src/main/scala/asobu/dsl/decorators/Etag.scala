package asobu.dsl.decorators

import asobu.dsl._
import org.joda.time.DateTime
import play.api.mvc.Result
import play.api.mvc.Results._
import CatsInstances._
import scala.concurrent.{ExecutionContext, Future}

//todo: need a test in syntax and example of actual usage here
object Etag {
  import Extractor._
  type Headers = Seq[(String, String)]

  /**
   *
   * @param processor
   * @param toHttpResult
   * @param getETag
   * @param ec
   * @tparam A
   * @tparam B
   * @return
   */
  def apply[A, B](
    processor: Extractor[A, B],
    toHttpResult: Extractor[B, Result]
  )(getETag: B ⇒ DateTime)(implicit ec: ExecutionContext): Extractor[(Headers, A), Result] = { (p: (Headers, A)) ⇒
    import play.api.http.HeaderNames.ETAG
    val (headers, a) = p
    val etagInHead: Option[Long] = headers.toMap.get(ETAG).map(_.toLong)
    val resultB: ExtractResult[B] = processor.run(a)
    for {
      b ← resultB
      etagInData = getETag(b).getMillis
      _ ← resultB.ensure(NotModified)(_ ⇒ etagInHead.fold(true)(_ != etagInData))
      r ← toHttpResult.run(b)
    } yield r.withHeaders(ETAG → etagInData.toString)
  }

}
