package fly.play.s3

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
import fly.play.libraryUtils.PlayConfiguration
import fly.play.aws.auth.AwsCredentials
import fly.play.aws.Aws
import play.api.libs.ws.Response

/**
 * Simple Storage Service
 */
object S3 {

  def apply(bucketName: String)(implicit credentials: AwsCredentials) = Bucket(credentials, bucketName)
  def apply(bucketName: String, delimiter: String)(implicit credentials: AwsCredentials) = Bucket(credentials, bucketName, Some(delimiter))

  private[s3] def put(credentials: AwsCredentials, bucketName: String, bucketFile: BucketFile): Promise[Response] = {
    val acl: String = bucketFile.acl.value

    Aws
      .withSigner(S3Signer(credentials))
      .url("http://" + bucketName + ".s3.amazonaws.com/" + bucketFile.name)
      .withHeaders("X-Amz-acl" -> acl)
      .put(bucketFile.content)
  }

  private[s3] def get(credentials: AwsCredentials, bucketName: String, path: Option[String], prefix: Option[String], delimiter: Option[String]): Promise[Response] =
    Aws
      .withSigner(S3Signer(credentials))
      .url("http://" + bucketName + ".s3.amazonaws.com/" + path.getOrElse(""))
      .withQueryString(
        (prefix.map("prefix" -> _).toList :::
          delimiter.map("delimiter" -> _).toList): _*)
      .get

      private[s3] def delete(credentials: AwsCredentials, bucketName: String, path: Option[String]): Promise[Response] =
      Aws
      .withSigner(S3Signer(credentials))
      .url("http://" + bucketName + ".s3.amazonaws.com/" + path.getOrElse(""))
    			  .delete
    			  
}

case class Error(status: Int, code: String, message: String, xml: Elem)
case class Success()

case class Bucket(
  credentials: AwsCredentials,
  name: String,
  delimiter: Option[String] = Some("/")) {

  def get(itemName: String): Promise[Either[Error, BucketFile]] =
    S3.get(credentials, name, Some(itemName), None, None) map convertResponse { r =>
      BucketFile(itemName, r.header("Content-Type").get, r.ahcResponse.getResponseBodyAsBytes)
    }

  def list: Promise[Either[Error, Iterable[BucketItem]]] =
    S3.get(credentials, name, None, None, delimiter) map listResponse

  def list(prefix: String): Promise[Either[Error, Iterable[BucketItem]]] =
    S3.get(credentials, name, None, Some(prefix), delimiter) map listResponse

  def +(bucketFile: BucketFile): Promise[Either[Error, Success]] =
    S3.put(credentials, name, bucketFile) map response

  def -(itemName: String): Promise[Either[Error, Success]] =
    S3.delete(credentials, name, Some(itemName)) map response

  def withDelimiter(delimiter: String): Bucket = copy(delimiter = Some(delimiter))
  def withDelimiter(delimiter: Option[String]): Bucket = copy(delimiter = delimiter)

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
