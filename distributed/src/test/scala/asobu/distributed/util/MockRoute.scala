package asobu.distributed.util

import play.routes.compiler._
import play.api.http.HttpVerbs._
//todo:
object MockRoute {
  def apply(
    pathParts: List[String] = List("abc", "ep1"),
    verb: String = GET,
    handlerClass: String = "HandlerClass"
  ): Route = {
    val call: HandlerCall = HandlerCall("test", handlerClass, false, "handle", None)
    call.setPos(null) //thanks for the mutable scala Positional, the default value NoPosition isn't serializable, this is not needed for the routes passed by the Routes Compiler

    val r = Route(HttpVerb(verb), PathPattern(pathParts.map(StaticPart)), call)
    r.setPos(null)
    r
  }
}
