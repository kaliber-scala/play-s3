package fly.play.aws

import java.nio.file.Files
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.ws._
import scala.concurrent.Future
import scala.concurrent.duration.Duration

case class AwsRequestHolder(wrappedRequest: WSRequest, signer: AwsSigner) extends WSRequest {

  def stream(): Future[StreamedResponse] =
    sign(method).flatMap(_.stream())

  @deprecated("8.0.0", """Deprecated in Play 2.5.0, use withMethod("GET").stream()""")
  def streamWithEnumerator(): Future[(WSResponseHeaders, Enumerator[Array[Byte]])] =
    sign(method).flatMap(_.streamWithEnumerator())

  def execute(): Future[WSResponse] =
    sign(method).flatMap(_.execute())

  private def sign(method: String) = {
    for {
      body <- getBodyFromRequest
    } yield signer.sign(wrappedRequest, method, body)
  }

  private def getBodyFromRequest: Future[Array[Byte]] =
    wrappedRequest.body match {
      case InMemoryBody(bytes)  => Future successful bytes.toArray
      case StreamedBody(source) => streamingBodyNotSupported
      case FileBody(file)       => Future successful Files.readAllBytes(file.toPath)
      case EmptyBody            => Future successful Array.empty[Byte]
    }

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

  def sign(calc: WSSignatureCalculator): WSRequest =
    copy(wrappedRequest = wrappedRequest sign calc)

  def withAuth(username: String, password: String, scheme: WSAuthScheme): WSRequest =
    copy(wrappedRequest = wrappedRequest.withAuth(username, password, scheme))

  def withBody(body: WSBody): WSRequest =
    copy(wrappedRequest = wrappedRequest withBody body)

  def withFollowRedirects(follow: Boolean): WSRequest =
    copy(wrappedRequest = wrappedRequest withFollowRedirects follow)

  def withHeaders(hdrs: (String, String)*): WSRequest =
    copy(wrappedRequest = wrappedRequest withHeaders (hdrs: _*))

  def withMethod(method: String): WSRequest =
    copy(wrappedRequest = wrappedRequest withMethod method)

  def withProxyServer(proxyServer: WSProxyServer): WSRequest =
    copy(wrappedRequest = wrappedRequest withProxyServer proxyServer)

  def withQueryString(parameters: (String, String)*): WSRequest =
    copy(wrappedRequest = wrappedRequest withQueryString (parameters: _*))

  def withRequestTimeout(timeout: Long): WSRequest =
    copy(wrappedRequest = wrappedRequest withRequestTimeout Duration(timeout, "second"))

  def withVirtualHost(vh: String): WSRequest =
    copy(wrappedRequest = wrappedRequest withVirtualHost vh)

  def withRequestFilter(filter: WSRequestFilter): WSRequest =
    copy(wrappedRequest = wrappedRequest withRequestFilter filter)

  def withRequestTimeout(timeout: Duration): WSRequest =
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
