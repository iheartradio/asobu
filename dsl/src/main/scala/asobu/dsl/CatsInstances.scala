package asobu.dsl

import cats.functor.Contravariant

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

trait CatsInstances extends cats.std.AllInstances {
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
