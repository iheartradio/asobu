package asobu

import akka.stream.Materializer
import akka.util.ByteString
import play.api.mvc.{ResponseHeader, Result}
import play.api.http.HttpEntity
import play.core.routing.RouteParams
import scala.concurrent.Future

package object distributed {
  type Headers = Seq[(String, String)] //Use Seq instead of Map to better ensure serialization

  type Body = play.api.mvc.AnyContent //use Array[Byte] or other serializable data structure

}
