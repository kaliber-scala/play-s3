package fly.play.s3

import fly.play.utils.PlayUtils._
import play.api.Play.current
import play.api.PlayException
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Promise
import play.api.libs.ws.WS
import play.api.libs.ws.Response
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang.StringUtils
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import scala.xml.Elem

object S3 {

  /** Default is ".s3.amazonaws.com/" */
  var endPoint = ".s3.amazonaws.com/"

  def apply(bucketName: String): Bucket = new Bucket(bucketName)
  def apply(bucketName: String, delimiter: String): Bucket = new Bucket(bucketName, Some(delimiter))

  private lazy val dateFormatter = {
    val dateFormatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss '+0000'", java.util.Locale.ENGLISH)
    dateFormatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
    dateFormatter
  }

  private def date: String = dateFormatter.format(new Date())

  private object authorization {

    private def hmacSha1(data: String): Array[Byte] = {
      val mac = Mac.getInstance("HmacSHA1")
      mac.init(new SecretKeySpec(S3.keys.secret.getBytes, mac.getAlgorithm))
      mac.doFinal(data.getBytes)
    }

    private def base64(data: Array[Byte]): String = new String(Base64.encodeBase64(data), "UTF-8");

    def md5(bytes: Array[Byte]): String = {
      val md = java.security.MessageDigest.getInstance("MD5")
      md.update(bytes)
      base64(md.digest)
    }

    //http://s3.amazonaws.com/doc/s3-developer-guide/RESTAuthentication.html
    def apply(verb: String, resource: String, amzHeaders: Option[String], contentMd5: Option[String], contentType: Option[String]): String = {
      val stringToSign =
        verb + "\n" +
          contentMd5.getOrElse("") + "\n" +
          contentType.getOrElse("") + "\n" +
          date + "\n" +
          amzHeaders.map(_ + "\n").getOrElse("") +
          resource

      //can not use logger in test (will be fixed in next release of play) uncomment line below to check signature for debugging
      //println(stringToSign)

      "AWS " + S3.keys.id + ":" + base64(hmacSha1(
        stringToSign))
    }
  }

  private def construct(bucketName: String, path: String, headers: List[(String, String)], authorization: String) =
    WS.url("http://" + bucketName + endPoint + path)
      .withHeaders((headers ::: /* add default headers */ List("Date" -> date, "Authorization" -> authorization)): _*)

  private[s3] def prepare(bucketName: String, bucketFile: BucketFile, verb: String): WS.WSRequestHolder = {
    val acl: String = bucketFile.acl.value
    val contentMd5 = authorization.md5(bucketFile.content)

    construct(
      bucketName,
      bucketFile.name,
      List(
        "x-amz-acl" -> acl,
        "Content-Type" -> bucketFile.contentType,
        "Content-MD5" -> contentMd5,
        "Content-Length" -> bucketFile.content.size.toString),
      authorization(verb, "/" + bucketName + "/" + bucketFile.name, Some("x-amz-acl:" + acl), Some(contentMd5), Some(bucketFile.contentType)))
  }

  private[s3] def prepare(bucketName: String, path: Option[String], prefix: Option[String], delimiter: Option[String], verb: String): WS.WSRequestHolder = {
    val actualPath = path getOrElse ""
    construct(bucketName,
      actualPath + List(prefix.map("prefix=" + _), delimiter.map("delimiter=" + _)).flatten.reduceOption(_ + "&" + _).map("?" + _).getOrElse(""),
      Nil,
      authorization(verb, "/" + bucketName + "/" + actualPath, None, None, None))
  }

  object keys {

    lazy val id = playConfiguration("s3.id")
    lazy val secret = playConfiguration("s3.secret")

  }
}

case class Error(status: Int, code: String, message: String, xml: Elem)
case class Success()

class Bucket(
  val name: String,
  val delimiter: Option[String] = Some("/")) {

  def get(itemName: String): Promise[Either[Error, BucketFile]] =
    S3.prepare(name, Some(itemName), None, None, "GET").get() map convertResponse { r =>
      BucketFile(itemName, r.header("Content-Type").get, r.ahcResponse.getResponseBodyAsBytes)
    }

  def list: Promise[Either[Error, Iterable[BucketItem]]] =
    S3.prepare(name, None, None, delimiter, "GET").get() map listResponse

  def list(prefix: String): Promise[Either[Error, Iterable[BucketItem]]] =
    S3.prepare(name, None, Some(prefix), delimiter, "GET").get() map listResponse

  def +(bucketFile: BucketFile): Promise[Either[Error, Success]] =
    S3.prepare(name, bucketFile, "PUT").put(bucketFile.content) map response

  def -(itemName: String): Promise[Either[Error, Success]] =
    S3.prepare(name, Some(itemName), None, None, "DELETE").delete() map response

  def withDelimiter(delimiter: String): Bucket = new Bucket(name, Some(delimiter))
  def withDelimiter(delimiter: Option[String]): Bucket = new Bucket(name, delimiter)

  private def convertResponse[T](converter: Response => T)(r: Response): Either[Error, T] =
    r.status match {
      case 200 | 204 => Right(converter(r))
      case status => Left(createError(status, r.xml))
    }

  private def createError(status: Int, xml: Elem): Error =
    xml.label match {
      case "Error" => Error(status, xml \ "Code" text, xml \ "Message" text, xml)
      case x => Error(status, "unknown body", x, xml)
    }

  private def listResponse =
    convertResponse { r =>
      val xml = r.xml

      /* files */ (xml \ "Contents").map(n => BucketItem(n \ "Key" text, false)) ++
        /* folders */ (xml \ "CommonPrefixes").map(n => BucketItem(n \ "Prefix" text, true))
    } _

  private def response = convertResponse { r => Success() } _
}

case class BucketItem(name: String, isVirtual: Boolean)
case class BucketFile(name: String, contentType: String, content: Array[Byte], acl: ACL = PUBLIC_READ)

case object PUBLIC_READ extends ACL("public-read")
case object PUBLIC_READ_WRITE extends ACL("public-read-write")
case object AUTHENTICATED_READ extends ACL("authenticated-read")
case object BUCKET_OWNER_READ extends ACL("bucket-owner-read")
case object BUCKET_OWNER_FULL_CONTROL extends ACL("bucket-owner-full-control")

sealed abstract class ACL(val value: String)
