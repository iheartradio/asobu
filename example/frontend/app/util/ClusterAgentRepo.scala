package util
import javax.inject._

import akka.actor.{ActorRef, Props, ActorSystem}
import akka.cluster.Cluster
import akka.routing.FromConfig
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

@Singleton
class ClusterAgentRepoProvider @Inject() (system: ActorSystem) extends Provider[ClusterAgentRepo]{

  val promise = Promise[ClusterAgentRepo]
  Cluster(system) registerOnMemberUp {
    val factorial = system.actorOf(FromConfig.props(), name = "factorialWorkerRouter")

    promise.success(ClusterAgentRepo(factorial))
  }

  override def get(): ClusterAgentRepo = Await.result(promise.future, 30.seconds)

}

case class ClusterAgentRepo(factorial: ActorRef)
