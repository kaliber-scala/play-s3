package fly.play.s3

import play.api.libs.ws.WS
import play.api.http.{ Writeable, ContentTypeOf }
import java.net.URI
import fly.play.aws.Aws
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import fly.play.aws.auth.{ AwsCredentials, Signer, SignerUtils }

case class S3Signer(credentials: AwsCredentials, s3o:S3 = S3.defaultS3) extends Signer with SignerUtils {
  private val AwsCredentials(accessKeyId, secretKey, sessionToken, expirationSeconds) = credentials
  
  val config = play.api.Play.current.configuration

  def sign(request: WS.WSRequestHolder, method: String): WS.WSRequestHolder =
    addAuthorizationHeaders(request, method, None, None)

  def sign[T](request: WS.WSRequestHolder, method: String, body: T)(implicit wrt: Writeable[T], ct: ContentTypeOf[T]): WS.WSRequestHolder =
    addAuthorizationHeaders(request, method, Some(wrt transform body), ct.mimeType)

  override def sign(data: Array[Byte], key: Array[Byte]): Array[Byte] = {
    val mac = Mac getInstance "HmacSHA1"
    mac init new SecretKeySpec(key, mac.getAlgorithm)
    mac doFinal data
  }

  override def hash(bytes: Array[Byte]): Array[Byte] = {
    val md = MessageDigest getInstance "MD5"
    md update bytes
    md.digest
  }

  private[s3] def addAuthorizationHeaders(request: WS.WSRequestHolder, method: String, body: Option[Array[Byte]], contentType: Option[String]): WS.WSRequestHolder = {

    import Aws.dates._

    val date = new Date
    val dateTime = rfc822DateFormat format date
    val contentMd5 = body.map(hash _ andThen base64Encode)

    var newHeaders = addHeaders(request.headers, dateTime, contentType, contentMd5)

    val uri = URI.create(request.url)
    var path = uri.getPath match {
      case "" | null => None
      case path => Some(path)
    }

    //we need to extract the bucket name from the host and use it in the resource path
    val BucketName = ("(.*?)" + ( "." + s3o.hostname ).replace(".","""\.""")).r
    val bucketName = uri.getHost match {
      case BucketName(name) => name
      case x => throw new Exception("Could not extract the bucket name from " + x)
    }

    val resourcePath = "/" + bucketName + path.getOrElse("")

    val cannonicalRequest = createCannonicalRequest(method, contentMd5, contentType, dateTime, newHeaders, resourcePath)

    val authorizationHeader = "AWS " + accessKeyId + ":" + createSignature(cannonicalRequest)

    newHeaders += "Authorization" -> Seq(authorizationHeader)

    request.copy(headers = newHeaders)
  }

  private[s3] def createSignature(cannonicalRequest: String) =
    base64Encode(sign(cannonicalRequest, secretKey))

  private[s3] def addHeaders(headers: Map[String, Seq[String]], dateTime: String, contentType: Option[String], contentMd5: Option[String]): Map[String, Seq[String]] = {
    var newHeaders = headers

    sessionToken foreach (newHeaders += "X-Amz-Security-Token" -> Seq(_))
    contentType foreach (newHeaders += "Content-Type" -> Seq(_))
    contentMd5 foreach (newHeaders += "Content-Md5" -> Seq(_))

    newHeaders += "Date" -> Seq(dateTime)

    newHeaders
  }

  private[s3] def createCannonicalRequest(method: String, contentMd5: Option[String], contentType: Option[String], dateTime: String, headers: Map[String, Seq[String]], resourcePath: String): String = {

    val elligableHeaders = headers.keys.filter(_.toLowerCase.startsWith("x-amz"))

    val sortedHeaders = elligableHeaders.toSeq.sorted

    val cannonicalRequest =
      method + "\n" +
        /* body md5 */
        contentMd5.getOrElse("") + "\n" +
        /* content type */
        contentType.getOrElse("") + "\n" +
        /* date */
        dateTime + "\n" +
        /* headers */
        sortedHeaders.map(k => k.toLowerCase + ":" + headers(k).mkString(",") + "\n").mkString +
        /* resourcePath */
        resourcePath

    cannonicalRequest
  }
}