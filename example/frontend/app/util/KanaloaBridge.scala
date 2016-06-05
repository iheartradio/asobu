package util

import javax.inject.Inject

import akka.actor.ActorSystem
import api.ErrorResult
import asobu.distributed.gateway.AbstractKanaloaBridge
import kanaloa.reactive.dispatcher._
import play.api.Configuration

class KanaloaBridge @Inject() (config: Configuration, system: ActorSystem) extends AbstractKanaloaBridge {

  override protected def resultChecker: ResultChecker = {
    case e: ErrorResult[_] ⇒ Left(e.toString)
    case m                ⇒ Right(m)
  }
}
