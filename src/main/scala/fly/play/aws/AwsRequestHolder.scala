package fly.play.aws

import java.io.File

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.ws.{WSRequest, _}
import play.api.mvc.MultipartFormData

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

case class AwsRequestHolder(wrappedRequest: WSRequest, signer: AwsSigner, implicit val executionContext: ExecutionContext) extends WSRequest {
  override type Response = WSResponse

  def stream(): Future[Response] =
    sign(method).flatMap(_.stream())

  def execute(): Future[Response] =
    sign(method).flatMap(_.execute())

  def execute(method: String): Future[Response] =
    sign(method).flatMap(_.execute())

  def head(): Future[Response] =
    withMethod("HEAD").execute()

  def get(): Future[Response] =
    withMethod("GET").execute()

  def post(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[Response] =
    withMethod("POST").withBody(body).execute()

  def post[T](body: T)(implicit evidence$3: BodyWritable[T]): Future[Response] =
    withMethod("POST").withBody(body).execute()

  def post(body: File): Future[Response] =
    withMethod("POST").withBody(body).execute()

  def put[T](body: T)(implicit evidence: BodyWritable[T]): Future[Response] =
    withMethod("PUT").withBody(body).execute()

  def put(body: File): Future[Response] =
    withMethod("PUT").withBody(body).execute()

  def put(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[Response] =
    withMethod("PUT").withBody(body).execute()

  def patch(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[Response] =
    withMethod("PATCH").withBody(body).execute()

  def patch[T](body: T)(implicit evidence$2: BodyWritable[T]): Future[Response] =
    withMethod("PATCH").withBody(body).execute()

  def patch(body: File): Future[Response] =
    withMethod("PATCH").withBody(body).execute()

  def delete(): Future[Response] =
    withMethod("DELETE").execute()

  def options(): Future[Response] =
    withMethod("OPTIONS").execute()

  private def sign(method: String) = {
    for {
      body <- getBodyFromRequest
    } yield signer.sign(wrappedRequest, method, body)
  }

  private def getBodyFromRequest: Future[Array[Byte]] =
    wrappedRequest.body match {
      case InMemoryBody(bytes)  => Future successful bytes.toArray
      case SourceBody(source)   => streamingBodyNotSupported
      case EmptyBody            => Future successful Array.empty[Byte]
    }

  val uri = wrappedRequest.uri
  val contentType = wrappedRequest.contentType
  val auth = wrappedRequest.auth
  val body = wrappedRequest.body
  val calc = wrappedRequest.calc
  val cookies = wrappedRequest.cookies
  val followRedirects = wrappedRequest.followRedirects
  val headers = wrappedRequest.headers
  val method = wrappedRequest.method
  val proxyServer = wrappedRequest.proxyServer
  val queryString = wrappedRequest.queryString
  val requestTimeout = wrappedRequest.requestTimeout
  val url = wrappedRequest.url
  val virtualHost = wrappedRequest.virtualHost

  def sign(calc: WSSignatureCalculator): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest sign calc)

  def withAuth(username: String, password: String, scheme: WSAuthScheme): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest.withAuth(username, password, scheme))

  def withBody(body: WSBody): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withBody body)

  def withBody(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withBody body)

  def withBody(file: File): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withBody body)

  def withBody[T](body: T)(implicit evidence$1: BodyWritable[T]): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withBody body)

  def withCookies(cookie: WSCookie*): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withCookies (cookie: _*))

  def withFollowRedirects(follow: Boolean): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withFollowRedirects follow)

  @deprecated("Deprecated in Play 2.6.0, use addHttpHeaders or addHttpHeaders", "9.0.0")
  def withHeaders(hdrs: (String, String)*): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withHeaders (hdrs: _*))

  def withHttpHeaders(hdrs: (String, String)*): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withHttpHeaders (hdrs: _*))

  def withMethod(method: String): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withMethod method)

  def withProxyServer(proxyServer: WSProxyServer): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withProxyServer proxyServer)

  @deprecated("Deprecated in Play 2.6.0, use addQueryStringParameters or addQueryStringParameters", "9.0.0")
  def withQueryString(parameters: (String, String)*): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withQueryString (parameters: _*))

  def withQueryStringParameters(parameters: (String, String)*): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withQueryStringParameters (parameters: _*))

  def withRequestTimeout(timeout: Long): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withRequestTimeout Duration(timeout, "second"))

  def withVirtualHost(vh: String): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withVirtualHost vh)

  def withRequestFilter(filter: WSRequestFilter): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withRequestFilter filter)

  def withRequestTimeout(timeout: Duration): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withRequestTimeout timeout)

  def withUrl(url: String): AwsRequestHolder =
    copy(wrappedRequest = wrappedRequest withUrl url)

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
