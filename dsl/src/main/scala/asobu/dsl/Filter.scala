package asobu.dsl

import cats._
import org.joda.time.DateTime
import play.api.cache.CacheApi
import play.api.mvc.Results._

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import CatsInstances._
import concurrent.ExecutionContext.Implicits.global
object Filter {

  implicit def filterMonoid[RMT] = new Monoid[Filter[RMT]] {
    def empty: Filter[RMT] = (_, result) ⇒ result

    def combine(x: Filter[RMT], y: Filter[RMT]): Filter[RMT] = { (req, result) ⇒
      y(req, x(req, result))
    }
  }
}

trait Filters {

  def eTag[T](getETag: T ⇒ DateTime): Filter[T] = { (req, result) ⇒
    import play.api.http.HeaderNames.ETAG
    import play.api.libs.concurrent.Execution.Implicits._
    val eTAGInRequest = req.headers.get(ETAG)
    val rse = getETag(req.body)
    eTAGInRequest match {
      case Some(e) if e.toLong == rse.getMillis ⇒ Future.successful(NotModified)
      case _                                    ⇒ result.map(_.withHeaders(ETAG → rse.getMillis.toString))
    }
  }
}

object Filters extends Filters
