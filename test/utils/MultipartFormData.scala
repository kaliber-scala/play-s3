package utils

import play.api.http.ContentTypeOf
import play.api.mvc.Codec
import play.api.http.Writeable

case class MultipartFormData(elements: Seq[NameValuePair], boundary: String)(
  implicit codec: Codec) {

  private val HTTP_SEPARATOR = "\r\n"
  private val actualBoundary = "--" + boundary
  private val endBoundary = actualBoundary + "--" + HTTP_SEPARATOR

  private val contentType = "multipart/form-data; boundary=" + boundary
  private val content = elements.map(toPart).mkString + endBoundary

  val body = Body(content)

  case class Body(content: String)

  object Body {
    implicit val contentTypeOf: ContentTypeOf[Body] =
      ContentTypeOf(Some(contentType))
    implicit val writes: Writeable[Body] =
      Writeable(body => codec.encode(body.content))
  }

  private def toPart(nameValuePair: NameValuePair) = {
    val (name, value) = nameValuePair
    actualBoundary + HTTP_SEPARATOR +
      "Content-Disposition: form-data; name=\"" + name + "\"" + HTTP_SEPARATOR +
      HTTP_SEPARATOR +
      value + HTTP_SEPARATOR
  }
}