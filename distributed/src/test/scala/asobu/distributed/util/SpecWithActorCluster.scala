package asobu.distributed.util

import akka.actor.ActorSystem
import akka.testkit.{TestKit, ImplicitSender, TestProbe}
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import org.specs2.specification.{Scope, AfterAll}

trait SpecWithActorCluster extends Specification with AfterAll {

  sequential
  implicit lazy val system = TestClusterActorSystem.create
  val role = TestClusterActorSystem.role
  def afterAll(): Unit = system.terminate()
}

object TestClusterActorSystem {
  val role = "test"
  def create = ActorSystem("test", ConfigFactory.parseString(
    s"""
      | akka {
      |   actor.provider = akka.cluster.ClusterActorRefProvider
      |   loglevel = "ERROR"
      |   cluster.roles = [ $role ]
      |   remote.netty.tcp.port = 0
      | }
      |
      | """.stripMargin
  ))
}

class ScopeWithActor(implicit system: ActorSystem) extends TestKit(system) with ImplicitSender with Scope
