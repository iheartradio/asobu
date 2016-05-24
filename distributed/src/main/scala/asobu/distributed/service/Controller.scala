package asobu.distributed.service

import akka.actor.ActorSystem
import asobu.distributed.gateway.Endpoint.Prefix
import asobu.distributed.EndpointDefinition
import play.routes.compiler.{HandlerCall, Route}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
 * Bare bone controller without syntax and facilitators, use [[DistributedController]] in normal cases
 */
trait Controller {
  /**
   * Used to get route file "$name.route"
   *
   * @return
   */
  def name: String = getClass.getName.split('$').last.split('.').last //todo: find a better way to get the canonical name of a class (getClass.getCanonicalName doesn't work for classes inside an object

  def actionName(shortName: String) = getClass.getName.stripSuffix("$").replace('$', '.') + "." + shortName //todo: a more solid name for action

  lazy val routes: List[Route] = EndpointDefinitionParser.parseResource(s"$name.routes") match {
    case Right(rs) ⇒ rs
    case Left(err) ⇒ throw RoutesParsingException(err.map(_.toString).mkString(". "))
  }

  def actions: List[Action]
}

