package asobu.distributed

trait RequestEnricherDefinition extends Serializable

trait RequestEnricherDefinitionCombinator extends RequestEnricherDefinition

object RequestEnricherDefinition {

  case class AndThen[A <: RequestEnricherDefinition, B <: RequestEnricherDefinition](a: A, b: B) extends RequestEnricherDefinitionCombinator
  case class OrElse[A <: RequestEnricherDefinition, B <: RequestEnricherDefinition](a: A, b: B) extends RequestEnricherDefinitionCombinator

  implicit class RequestEnricherDefinitionSyntax[A <: RequestEnricherDefinition](a: A) {
    def andThen[B <: RequestEnricherDefinition](b: B): AndThen[A, B] = AndThen(a, b)
    def orElse[B <: RequestEnricherDefinition](b: B): OrElse[A, B] = OrElse(a, b)
  }
}
