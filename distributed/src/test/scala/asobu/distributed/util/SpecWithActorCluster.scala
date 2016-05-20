package asobu.distributed.util

import akka.actor.ActorSystem
import akka.testkit.{TestKit, ImplicitSender, TestProbe}
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import org.specs2.specification.{Scope, AfterAll}

import scala.util.Random

trait SpecWithActorCluster extends Specification with AfterAll {
  sequential
  lazy val port = Random.nextInt(21444) + 2560
  implicit lazy val system = {
    TestClusterActorSystem.create(port)
  }
  lazy val role = TestClusterActorSystem.role

  override def afterAll(): Unit = {
    system.terminate()
  }
}

object TestClusterActorSystem {

  val role = "test"
  def create(port: Int = 2551) = {
    ActorSystem("test", ConfigFactory.parseString(
      s"""
         | akka {
         |   actor.provider = akka.cluster.ClusterActorRefProvider
         |   loglevel = "ERROR"
         |   cluster {
         |     seed-nodes = ["akka.tcp://application@127.0.0.1:$port"]
         |     roles = [ $role ]
         |     min-nr-of-members = 1
         |   }
         |   remote.netty.tcp {
         |     hostname = 127.0.0.1
         |     port = $port
         |   }
         |   extensions = [ "akka.cluster.metrics.ClusterMetricsExtension", "akka.cluster.ddata.DistributedData"]
         | }
         |
      | """.stripMargin
    ))
  }
}

class ScopeWithActor(implicit system: ActorSystem) extends TestKit(system) with ImplicitSender with Scope
