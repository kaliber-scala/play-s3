package fly.play.aws

import akka.stream.scaladsl.Source
import akka.util.ByteString
import java.io.File
import java.nio.file.Files
import play.api.libs.ws.{ WSRequest, _ }
import play.api.mvc.MultipartFormData
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration

case class AwsRequestHolder(wrappedRequest: WSRequest, signer: AwsSigner, implicit val executionContext: ExecutionContext) extends WSRequest {
  type Self = AwsRequestHolder
  type Response = WSResponse

  def stream(): Future[StreamedResponse] =
    sign(method).flatMap(_.stream())

  def execute(): Future[Response] =
    sign(method).flatMap(_.execute())

  def execute(method: String): Future[Response] =
    sign(method).flatMap(_.execute())

  def head(): Future[Response] =
    sign(method).flatMap(_.head())

  def get(): Future[Response] =
    sign(method).flatMap(_.get())

  def post(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[Response] =
    sign(method).flatMap(_.post(body))

  def post[T](body: T)(implicit evidence$3: BodyWritable[T]): Future[Response] =
    sign(method).flatMap(_.post(body))

  def post(body: File): Future[Response] =
    sign(method).flatMap(_.post(body))

  def put[T](body: T)(implicit evidence: BodyWritable[T]): Future[Response] =
    sign(method).flatMap(_.put(body))

  def put(body: File): Future[Response] =
    sign(method).flatMap(_.put(body))

  def put(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[Response] =
    sign(method).flatMap(_.put(body))

  def patch(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[Response] =
    sign(method).flatMap(_.patch(body))

  def patch[T](body: T)(implicit evidence$2: BodyWritable[T]): Future[Response] =
    sign(method).flatMap(_.patch(body))

  def patch(body: File): Future[Response] =
    sign(method).flatMap(_.patch(body))

  def delete(): Future[Response] =
    sign(method).flatMap(_.delete())

  def options(): Future[Response] =
    sign(method).flatMap(_.options())

  private def sign(method: String) = {
    for {
      body <- getBodyFromRequest
      signedRequest = signer.sign(wrappedRequest, method, body)
    } yield copy(wrappedRequest = signedRequest)
  }

  private def getBodyFromRequest: Future[Array[Byte]] =
    wrappedRequest.body match {
      case InMemoryBody(bytes)  => Future successful bytes.toArray
      case StreamedBody(source) => streamingBodyNotSupported
      case FileBody(file)       => Future successful Files.readAllBytes(file.toPath)
      case EmptyBody            => Future successful Array.empty[Byte]
    }

  val uri = wrappedRequest.uri
  val contentType = wrappedRequest.contentType
  val auth = wrappedRequest.auth
  val body = wrappedRequest.body
  val calc = wrappedRequest.calc
  val followRedirects = wrappedRequest.followRedirects
  val headers = wrappedRequest.headers
  val method = wrappedRequest.method
  val proxyServer = wrappedRequest.proxyServer
  val queryString = wrappedRequest.queryString
  val requestTimeout = wrappedRequest.requestTimeout
  val url = wrappedRequest.url
  val virtualHost = wrappedRequest.virtualHost

  def sign(calc: WSSignatureCalculator): Self =
    copy(wrappedRequest = wrappedRequest sign calc)

  def withAuth(username: String, password: String, scheme: WSAuthScheme): Self =
    copy(wrappedRequest = wrappedRequest.withAuth(username, password, scheme))

  def withBody(body: WSBody): Self =
    copy(wrappedRequest = wrappedRequest withBody body)

  def withBody(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Self =
    copy(wrappedRequest = wrappedRequest withBody body)

  def withBody(file: File): Self =
    copy(wrappedRequest = wrappedRequest withBody body)

  def withBody[T](body: T)(implicit evidence$1: BodyWritable[T]): Self =
    copy(wrappedRequest = wrappedRequest withBody body)

  def withFollowRedirects(follow: Boolean): Self =
    copy(wrappedRequest = wrappedRequest withFollowRedirects follow)

  def withHeaders(hdrs: (String, String)*): Self =
    copy(wrappedRequest = wrappedRequest withHeaders (hdrs: _*))

  def withMethod(method: String): Self =
    copy(wrappedRequest = wrappedRequest withMethod method)

  def withProxyServer(proxyServer: WSProxyServer): Self =
    copy(wrappedRequest = wrappedRequest withProxyServer proxyServer)

  def withQueryString(parameters: (String, String)*): Self =
    copy(wrappedRequest = wrappedRequest withQueryString (parameters: _*))

  def withRequestTimeout(timeout: Long): Self =
    copy(wrappedRequest = wrappedRequest withRequestTimeout Duration(timeout, "second"))

  def withVirtualHost(vh: String): Self =
    copy(wrappedRequest = wrappedRequest withVirtualHost vh)

  def withRequestFilter(filter: WSRequestFilter): Self =
    copy(wrappedRequest = wrappedRequest withRequestFilter filter)

  def withRequestTimeout(timeout: Duration): Self =
    copy(wrappedRequest = wrappedRequest withRequestTimeout timeout)

  private def streamingBodyNotSupported =
    sys error
      """|A streaming body in the request is currently not supported. We could
         |add a mechanism that reads all bytes using a materialized `Sink`, but
         |that would involve passing in an `ActorSystem`.
         |
         |We seriously doubt that a streaming body in combination with signing
         |it is not the way to go: it would negate the effect of streaming.
         |Instead of silently filling the memory with these bytes we throw the
         |error you are reading. As a bonus the interface of this library is
         |simpeler (it is not dependent on an `ActorSystem` for signing requests).
         |""".stripMargin
}
