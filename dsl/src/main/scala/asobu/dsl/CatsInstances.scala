package asobu.dsl

import cats.functor.Contravariant
import cats.instances._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

//todo go back to AllInstances
trait CatsInstances extends FunctionInstances
    with StringInstances
    with EitherInstances
    with ListInstances
    with OptionInstances
    with SetInstances
    with StreamInstances
    with VectorInstances
    with MapInstances
    with BigIntInstances
    with BigDecimalInstances
    with FutureInstances
    with TryInstances {
  implicit def partialFunctionContravariant[R]: Contravariant[PartialFunction[?, R]] =
    new Contravariant[PartialFunction[?, R]] {
      def contramap[T1, T0](pa: PartialFunction[T1, R])(f: T0 ⇒ T1) = new PartialFunction[T0, R] {
        def isDefinedAt(x: T0): Boolean = pa.isDefinedAt(f(x))
        def apply(x: T0): R = pa(f(x))
      }
    }

}

object CatsInstances extends CatsInstances

case object LocalExecutionContext extends ExecutionContext with Serializable {

  implicit val instance: ExecutionContext = this

  override def execute(command: Runnable): Unit = command.run()

  override def reportFailure(cause: Throwable): Unit = throw cause
}
