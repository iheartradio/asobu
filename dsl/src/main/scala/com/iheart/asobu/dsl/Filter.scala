package asobu.dsl

import cats._
import org.joda.time.DateTime
import play.api.cache.CacheApi
import syntax.all._
import play.api.mvc.Results._

import scala.concurrent.Future
import scala.concurrent.duration.Duration

object Filter {

  implicit def filterMonoid[RMT] = new Monoid[Filter[RMT]] {
    def empty: Filter[RMT] = (_, result) ⇒ result

    def combine(x: Filter[RMT], y: Filter[RMT]): Filter[RMT] = { (req, result) ⇒
      y(req, x(req, result))
    }
  }
}

trait Filters {

  def cached[RMT](duration: Duration)(implicit cache: CacheApi): Filter[RMT] =
    (req, result) ⇒ cache.getOrElse(req.body.toString)(result)

  def eTag[T](getETag: T ⇒ DateTime): Filter[T] = { (req, result) ⇒
    import play.api.libs.concurrent.Execution.Implicits._
    import play.api.http.HeaderNames.ETAG
    val eTAGInRequest = req.headers.get(ETAG)
    val rse = getETag(req.body)
    eTAGInRequest match {
      case Some(e) if e.toLong == rse.getMillis ⇒ Future.successful(NotModified)
      case _                                    ⇒ result.map(_.withHeaders(ETAG → rse.getMillis.toString))
    }
  }
}

object Filters extends Filters
