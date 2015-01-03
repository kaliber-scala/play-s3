package fly.play.aws

import play.api.libs.ws.WSRequestHolder
import play.api.libs.ws.WSSignatureCalculator
import play.api.libs.ws.WSAuthScheme
import play.api.libs.ws.WSBody
import play.api.libs.ws.WSResponseHeaders
import scala.concurrent.Future
import play.api.libs.iteratee.Enumerator
import play.api.libs.ws.WSResponse
import play.api.libs.ws.WSProxyServer
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.StreamedBody
import play.api.libs.ws.InMemoryBody
import java.io.RandomAccessFile
import play.api.libs.ws.FileBody
import java.io.File
import play.api.libs.ws.EmptyBody
import play.api.libs.iteratee.Iteratee

case class AwsRequestHolder(wrappedRequest: WSRequestHolder, signer: AwsSigner) extends WSRequestHolder {

  def stream(): Future[(WSResponseHeaders, Enumerator[Array[Byte]])] =
    sign(method).flatMap(_.stream())

  def execute(): Future[WSResponse] =
    sign(method).flatMap(_.execute())

  private def sign(method: String) = {
    for {
      body <- getBodyFromRequest
    } yield signer.sign(wrappedRequest, method, body)
  }

  private def getBodyFromRequest: Future[Array[Byte]] =
    wrappedRequest.body match {
      case InMemoryBody(bytes)      => Future successful bytes
      case StreamedBody(enumerator) => enumerator run Iteratee.consume[Array[Byte]]()
      case FileBody(file)           => Future successful fileToByteArray(file)
      case EmptyBody                => Future successful Array.empty[Byte]
    }

  def fileToByteArray(file: File) = {
    val randomAccessFile = new RandomAccessFile(file, "r")
    val byteArray = new Array[Byte](randomAccessFile.length.toInt)
    randomAccessFile read byteArray
    byteArray
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

  def sign(calc: WSSignatureCalculator): WSRequestHolder =
    copy(wrappedRequest = wrappedRequest sign calc)

  def withAuth(username: String, password: String, scheme: WSAuthScheme): WSRequestHolder =
    copy(wrappedRequest = wrappedRequest.withAuth(username, password, scheme))

  def withBody(body: WSBody): WSRequestHolder =
    copy(wrappedRequest = wrappedRequest withBody body)

  def withFollowRedirects(follow: Boolean): WSRequestHolder =
    copy(wrappedRequest = wrappedRequest withFollowRedirects follow)

  def withHeaders(hdrs: (String, String)*): WSRequestHolder =
    copy(wrappedRequest = wrappedRequest withHeaders (hdrs: _*))

  def withMethod(method: String): WSRequestHolder =
    copy(wrappedRequest = wrappedRequest withMethod method)

  def withProxyServer(proxyServer: WSProxyServer): WSRequestHolder =
    copy(wrappedRequest = wrappedRequest withProxyServer proxyServer)

  def withQueryString(parameters: (String, String)*): WSRequestHolder =
    copy(wrappedRequest = wrappedRequest withQueryString (parameters: _*))

  def withRequestTimeout(timeout: Int): WSRequestHolder =
    copy(wrappedRequest = wrappedRequest withRequestTimeout timeout)

  def withVirtualHost(vh: String): WSRequestHolder =
    copy(wrappedRequest = wrappedRequest withVirtualHost vh)
}