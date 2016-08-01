package asobu.dsl

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.Await
import org.specs2.specification.{After, Scope}
import scala.concurrent.duration.Duration

trait WithSystem extends Scope with After {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  def after = Await.result(system.terminate(), Duration.Inf)
}
