package backend.school

import akka.actor.{Props, ActorLogging, Actor}
import api.FactorialService.{Result, Compute}
import backend.models.Student
import backend.school.StudentService.Grade

import scala.annotation.tailrec
import scala.concurrent.Future

class StudentService extends Actor with ActorLogging {

  import context.dispatcher

  def receive = {
    case Student(name, age) =>
      sender ! Grade(age / 2)
  }

}

object StudentService {

  def props = Props(new StudentService)

  case class Grade(value: Int)
}
