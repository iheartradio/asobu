package asobu.dsl.akka

import _root_.akka.actor.{ActorRef, ActorSelection}
import _root_.akka.pattern.ask
import _root_.akka.util.Timeout
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
