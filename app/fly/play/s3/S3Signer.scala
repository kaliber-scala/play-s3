package fly.play.s3

import java.net.URI
import java.security.MessageDigest
import java.util.Date

import fly.play.aws.Aws.dates.rfc822DateFormat
import fly.play.aws.auth.AwsCredentials
import fly.play.aws.auth.Signer
import fly.play.aws.auth.SignerUtils
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import play.api.http.ContentTypeOf
import play.api.http.Writeable
import play.api.libs.ws.WS

case class S3Signer(credentials: AwsCredentials, s3Host: String) extends Signer with SignerUtils {
  private val AwsCredentials(accessKeyId, secretKey, sessionToken, expirationSeconds) = credentials

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

    val date = new Date
    val dateTime = rfc822DateFormat format date

    val contentMd5 = body.flatMap {
      case Array() => None
      case data => Some(base64Encode(hash(data)))
    }

    var newHeaders = addHeaders(request.headers, dateTime, contentType, contentMd5)

    val uri = URI.create(request.url)
    var path = uri.getPath match {
      case "" | null => None
      case path => Some(path)
    }

    //we need to extract the bucket name from the host and use it in the resource path
    val escapedS3Host = s3Host.replace(".", raw"\.")
    val BucketNameRegex = s"(.*?)\\.$escapedS3Host".r
    val bucketName =
      uri.getHost match {
        case BucketNameRegex(name) => name
        case x => throw new Exception("Could not extract the bucket name from " + x)
      }

    val resourcePath = "/" + bucketName + path.getOrElse("")

    val canonicalRequest = createCanonicalRequest(method, contentMd5, contentType, dateTime, newHeaders, resourcePath, request.queryString)

    val authorizationHeader = "AWS " + accessKeyId + ":" + createSignature(canonicalRequest)

    newHeaders += "Authorization" -> Seq(authorizationHeader)

    request.copy(headers = newHeaders)
  }

  def createSignature(string: String) =
    base64Encode(sign(string, secretKey))

  private[s3] def addHeaders(headers: Map[String, Seq[String]], dateTime: String, contentType: Option[String], contentMd5: Option[String]): Map[String, Seq[String]] = {
    var newHeaders = headers

    sessionToken foreach (newHeaders += "X-Amz-Security-Token" -> Seq(_))
    contentType foreach (newHeaders += "Content-Type" -> Seq(_))
    contentMd5 foreach (newHeaders += "Content-Md5" -> Seq(_))

    newHeaders += "Date" -> Seq(dateTime)

    newHeaders
  }

  private[s3] val subResourceNames = Set(
    "acl", "lifecycle", "location", "logging", "notification", "partNumber", "policy",
    "requestPayment", "torrent", "uploadId", "uploads", "versionId", "versioning",
    "versions", "website")

  private[s3] def getResourceQueryString(queryString: Map[String, Seq[String]]): Option[String] = {
    val filtered =
      queryString
        .filterKeys(subResourceNames)
        
    if (filtered.isEmpty) None
    else {
      // do not url encode, that would make the signature invalid
      val result = filtered
        .map {
          case (k, v) if (v.isEmpty || v.head == "") => k
          case (k, v) => k + "=" + v.head
        }
      	.toSeq.sorted
      	.mkString("&")
      
      Some("?" + result)
    }
    
  }

  private[s3] def createCanonicalRequest(method: String, contentMd5: Option[String], contentType: Option[String], dateTime: String, headers: Map[String, Seq[String]], resourcePath: String, queryString: Map[String, Seq[String]]): String = {

    val elligableHeaders = headers.keys.filter(_.toLowerCase.startsWith("x-amz"))

    val sortedHeaders = elligableHeaders.toSeq.sorted

    val resourceQueryString = getResourceQueryString(queryString)

    val canonicalRequest =
      method + "\n" +
        /* body md5 */
        contentMd5.getOrElse("") + "\n" +
        /* content type */
        contentType.getOrElse("") + "\n" +
        /* date */
        dateTime + "\n" +
        /* headers */
        sortedHeaders.map(k => k.toLowerCase + ":" + headers(k).mkString(",") + "\n").mkString +
        /* resourcePath + resourceQueryString */
        resourcePath + resourceQueryString.getOrElse("")

    canonicalRequest
  }
}
