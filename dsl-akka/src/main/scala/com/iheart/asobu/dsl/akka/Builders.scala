package asobu.dsl.akka

import _root_.akka.actor.{ActorSelection, ActorRef}
import _root_.akka.util.Timeout
import _root_.akka.pattern.ask
import asobu.dsl.SyntaxFacilitators._

trait Builders {
  implicit def actorAskBuilder(implicit to: Timeout) = new AskableBuilder[ActorRef] {
    def apply(t: ActorRef): Askable = t.ask
  }
  implicit def actorSelectionAskBuilder(implicit to: Timeout) = new AskableBuilder[ActorSelection] {
    def apply(t: ActorSelection): Askable = t.ask
  }
}

object Builders extends Builders
