package asobu.distributed.service

import asobu.dsl.CatsInstances
import cats.data.Xor
import org.joda.time.DateTime
import play.api.mvc.{QueryStringBindable, PathBindable}
import play.core.routing.RouteParams
import cats.syntax.all._
import CatsInstances._

import scala.util.Try

trait RouteParamRead[V] extends Serializable {
  def apply(field: String, params: RouteParams): Xor[String, V]
}

//This whole thing is needed because PathBindable and QueryStringBindable isn't serializable
object RouteParamRead {

  def apply[V](implicit rpe: RouteParamRead[V]): RouteParamRead[V] = rpe

  def mk[V](f: ⇒ (String, RouteParams) ⇒ Xor[String, V]): RouteParamRead[V] = new RouteParamRead[V] {
    def apply(field: String, params: RouteParams): Xor[String, V] = {
      f(field, params)
    }
  }

  implicit val rpeInt: RouteParamRead[Int] = mk(ext[Int])
  implicit val rpeLong: RouteParamRead[Long] = mk(ext[Long])
  implicit val rpeDouble: RouteParamRead[Double] = mk(ext[Double])
  implicit val rpeFloat: RouteParamRead[Float] = mk(ext[Float])
  implicit val rpeBoolean: RouteParamRead[Boolean] = mk(ext[Boolean])
  implicit val rpeString: RouteParamRead[String] = mk(ext[String])

  implicit def rpeOption[T: RouteParamRead]: RouteParamRead[Option[T]] = new RouteParamRead[Option[T]] {
    def apply(field: String, params: RouteParams): Xor[String, Option[T]] = {
      val r = implicitly[RouteParamRead[T]]
      r(field, params).map(Some(_)) orElse Xor.right(None)
    }
  }

  implicit val rpeDateTime: RouteParamRead[DateTime] = new RouteParamRead[DateTime] {
    def apply(field: String, params: RouteParams): Xor[String, DateTime] = {
      for {
        strValue ← rpeString(field, params)
        longValue ← Xor.fromTry(Try(strValue.toLong)).leftMap(_ ⇒ s"Cannot parse $field value $strValue")
      } yield new DateTime(longValue)

    }
  }

  def ext[V: PathBindable: QueryStringBindable] = (field: String, params: RouteParams) ⇒ {
    params.fromPath[V](field).value.toXor orElse params.fromQuery[V](field).value.toXor
  }

}

