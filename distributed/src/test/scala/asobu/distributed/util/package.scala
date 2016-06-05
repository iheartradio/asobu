package asobu.distributed

import asobu.distributed.CustomRequestExtractorDefinition.Interpreter
import asobu.dsl._

package object util {

  object implicits {
    implicit val nullInterpreter = new Interpreter {
      override def interpret[T](cred: CustomRequestExtractorDefinition[T]): RequestExtractor[T] = throw new Exception(s"Interpreter for $cred is not implemented yet")
    }
  }

}
