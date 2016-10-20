package asobu.distributed

import akka.util.ByteString
import play.api.libs.json.{Json, JsValue}
import play.api.mvc.RawBuffer
import play.api.test.{FakeRequest, FakeHeaders, PlaySpecification}

trait FakeRequests {
  def request(
    method: String = "GET",
    url: String = "/"
  ) = {
    FakeRequest(method, url).withBody(RawBuffer(0))
  }

  def rawJson(json: JsValue): RawBuffer = {
    val bytes = Json.stringify(json).getBytes()
    RawBuffer(bytes.length, ByteString(bytes))
  }

}
