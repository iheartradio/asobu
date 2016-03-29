package asobu.distributed.util

import javax.inject.{Inject, Provider, Singleton}

import akka.actor._
import akka.cluster.Cluster
import asobu.distributed.gateway.{Gateway, Endpoint}
import play.api.libs.json.Json
import play.api.{Configuration, Environment}
import play.api.inject.Module
import play.api.mvc._

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

case class MockRequest(path: String, headers: Headers = Headers()) extends Request[AnyContent] {
  def body: AnyContent = AnyContentAsJson(Json.obj())
  def secure: Boolean = ???
  def uri: String = ???
  def remoteAddress: String = ???
  def queryString: Map[String, Seq[String]] = Map.empty
  def method: String = "GET"
  def version: String = ???
  def tags: Map[String, String] = ???
  def id: Long = ???
}

@Singleton
class TestRequestsScheduler @Inject() (system: ActorSystem, gateway: Gateway) {
  import system.dispatcher
  system.scheduler.schedule(Duration.Zero, 10.seconds, gateway.entryActor, MockRequest("/ep1/a/1", headers = Headers("bar" → "BBBB")))

}

class TestRequestsSchedulerModule extends Module {

  def bindings(
    environment: Environment,
    configuration: Configuration
  ) = Seq(
    bind[TestRequestsScheduler].toSelf.eagerly
  )
}
