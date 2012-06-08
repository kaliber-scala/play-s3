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
import fly.play.aws.xml.AwsResponse
import fly.play.aws.xml.AwsError

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

case class Success()

case class Bucket(
  credentials: AwsCredentials,
  name: String,
  delimiter: Option[String] = Some("/")) {

  def get(itemName: String): Promise[Either[AwsError, BucketFile]] =
    S3.get(credentials, name, Some(itemName), None, None) map AwsResponse { (status, response) =>
      BucketFile(itemName, response.header("Content-Type").get, response.ahcResponse.getResponseBodyAsBytes)
    }

  def list: Promise[Either[AwsError, Iterable[BucketItem]]] =
    S3.get(credentials, name, None, None, delimiter) map listResponse

  def list(prefix: String): Promise[Either[AwsError, Iterable[BucketItem]]] =
    S3.get(credentials, name, None, Some(prefix), delimiter) map listResponse

  def + = add _
  def add(bucketFile: BucketFile): Promise[Either[AwsError, Success]] = 
    S3.put(credentials, name, bucketFile) map successResponse
    
  def - = remove _
  def remove(itemName: String): Promise[Either[AwsError, Success]] =
    S3.delete(credentials, name, Some(itemName)) map successResponse

  def withDelimiter(delimiter: String): Bucket = copy(delimiter = Some(delimiter))
  def withDelimiter(delimiter: Option[String]): Bucket = copy(delimiter = delimiter)

  private def listResponse =
    AwsResponse { (status, response) =>
      val xml = response.xml

      /* files */ (xml \ "Contents").map(n => BucketItem(n \ "Key" text, false)) ++
        /* folders */ (xml \ "CommonPrefixes").map(n => BucketItem(n \ "Prefix" text, true))
    } _

  private def successResponse = AwsResponse { (status, response) => Success() } _
}

case class BucketItem(name: String, isVirtual: Boolean)
case class BucketFile(name: String, contentType: String, content: Array[Byte], acl: ACL = PUBLIC_READ)

case object PUBLIC_READ extends ACL("public-read")
case object PUBLIC_READ_WRITE extends ACL("public-read-write")
case object AUTHENTICATED_READ extends ACL("authenticated-read")
case object BUCKET_OWNER_READ extends ACL("bucket-owner-read")
case object BUCKET_OWNER_FULL_CONTROL extends ACL("bucket-owner-full-control")

sealed abstract class ACL(val value: String)
