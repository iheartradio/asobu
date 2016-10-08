package asobu.distributed.protocol

import akka.stream.Materializer
import akka.util.ByteString
import asobu.distributed.Headers

import play.api.http.HttpEntity
import play.api.mvc._

import scala.concurrent.Future

@SerialVersionUID(1L)
case class HttpStatus(code: Int) extends AnyVal

@SerialVersionUID(1L)
case class DRequest(
  requestParams: RequestParams,
  body: Array[Byte],
  headers: Headers = Nil
)

@SerialVersionUID(1L)
case class RequestParams(
  pathParams: Map[String, String],
  queryString: Map[String, Seq[String]]
)

@SerialVersionUID(1L)
case class DResult(
  status: HttpStatus,
  headers: Headers = Nil,
  body: Array[Byte] = Array.empty
)

object DRequest {
  def apply(params: RequestParams, request: Request[RawBuffer]): DRequest = {
    DRequest(
      params,
      request.body.asBytes().fold(Array[Byte]())(_.toArray), //todo: evaluate the performance impact of copying the whole body in memory
      request.headers.headers
    )
  }

}

object RequestParams {
  lazy val empty = RequestParams(Map.empty, Map.empty)
}

object DResult {
  def toResult(dr: DResult): Result =
    Result(new ResponseHeader(dr.status.code, dr.headers.toMap), HttpEntity.Strict(ByteString(dr.body), None))

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
