package asobu.dsl.util

import cats.data.Validated
import simulacrum.typeclass

import scala.util.Try

@typeclass
sealed trait Read[T] extends Serializable {
  def parse(str: String): Try[T]
}

trait ReadInstances {
  type ParseResult[T] = Try[T]

  def apply[T](f: String ⇒ T): Read[T] = new Read[T] {
    def parse(str: String): Try[T] = Try(f(str))
  }

  implicit val strReader: Read[String] = apply[String](identity)
  implicit val intReader: Read[Int] = apply(_.toInt)
  implicit val longReader: Read[Long] = apply(_.toLong)
  implicit val doubleReader: Read[Double] = apply(_.toDouble)
  implicit val booleanReader: Read[Boolean] = apply(_.toBoolean)

}

object Read extends ReadInstances

object ReadInstances extends ReadInstances
