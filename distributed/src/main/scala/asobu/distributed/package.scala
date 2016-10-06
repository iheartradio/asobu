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

package distributed {

  import play.api.mvc.{Request, AnyContent}

  case class HttpStatus(code: Int) extends AnyVal

  case class DRequest(
    requestParams: RequestParams,
    body: Body,
    headers: Headers = Nil
  )

  object DRequest {
    def apply(params: RequestParams, request: Request[Body]): DRequest =
      DRequest(params, request.body, request.headers.headers)
  }

  case class RequestParams(
    pathParams: Map[String, String],
    queryString: Map[String, Seq[String]]
  )

  object RequestParams {
    lazy val empty = RequestParams(Map.empty, Map.empty)
  }

  case class DResult(
      status: HttpStatus,
      headers: Headers = Nil,
      body: Array[Byte] = Array.empty
  ) {
    def toResult =
      Result(new ResponseHeader(status.code, headers.toMap), HttpEntity.Strict(ByteString(body), None))
  }

  object DResult {

    def from(result: Result)(implicit mat: Materializer): Future[DResult] = {
      import scala.concurrent.ExecutionContext.Implicits.global
      result.body.consumeData.map { data ⇒
        DResult(
          HttpStatus(result.header.status),
          result.header.headers.toSeq,
          data.toArray[Byte]
        )
      }
    }

  }
}
