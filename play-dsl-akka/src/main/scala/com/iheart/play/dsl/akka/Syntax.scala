package com.iheart.play.dsl.akka

import akka.actor.{ActorSelection, ActorRef}
import akka.util.Timeout
import akka.pattern.ask
import com.iheart.play.dsl.Syntax._

object Syntax {
  implicit def actorAskBuilder(implicit to: Timeout) = new AskableBuilder[ActorRef] {
    def apply(t: ActorRef): Askable = t.ask
  }
   implicit def actorSelectionAskBuilder(implicit to: Timeout) = new AskableBuilder[ActorSelection] {
    def apply(t: ActorSelection): Askable = t.ask
  }
}
