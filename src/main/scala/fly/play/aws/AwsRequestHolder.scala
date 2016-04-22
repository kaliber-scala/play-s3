package fly.play.aws

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import java.io.File
import java.io.RandomAccessFile
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.ws._
import scala.concurrent.Future
import scala.concurrent.duration.Duration

case class AwsRequestHolder(wrappedRequest: WSRequest, signer: AwsSigner) extends WSRequest {

  implicit private val actorSystem = ActorSystem("aws-request-holder")
  implicit private val materializer = ActorMaterializer()

  private val streamedBodySink = Sink.fold[Array[Byte], ByteString](Array.empty[Byte])(_ ++ _)

  def stream(): Future[StreamedResponse] =
    sign(method).flatMap(_.stream())

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
      case InMemoryBody(bytes)      => Future successful bytes.toArray
      case StreamedBody(source)     => source.runWith(streamedBodySink)
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
}
