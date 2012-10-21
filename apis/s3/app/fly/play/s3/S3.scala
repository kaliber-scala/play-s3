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
import play.api.http.ContentTypeOf

/**
 * Amazon Simple Storage Service
 */
object S3 {

  /**
   * Utility method to create a bucket.
   *
   * @see Bucket
   */
  def apply(bucketName: String)(implicit credentials: AwsCredentials): Bucket = Bucket(bucketName)
  /**
   * Utility method to create a bucket.
   *
   * @see Bucket
   */
  def apply(bucketName: String, delimiter: String)(implicit credentials: AwsCredentials): Bucket = Bucket(bucketName, Some(delimiter))

  private def httpUrl(bucketName: String, path: String) =
    "http://" + bucketName + ".s3.amazonaws.com/" + path

  /**
   * Lowlevel method to call put on a bucket in order to store a file
   *
   * @param bucketName	The name of the bucket
   * @param bucketFile	The file that you want to store
   *
   * @see Bucket.add
   */
  def put(bucketName: String, bucketFile: BucketFile)(implicit credentials: AwsCredentials): Promise[Response] = {
    val acl: Option[ACL] = bucketFile.acl

    require(acl.isDefined, "Can not add a file to a bucket without an ACL defined")

    implicit val fileContentType = ContentTypeOf[Array[Byte]](Some(bucketFile.contentType))
    
    val request = Aws
      .withSigner(S3Signer(credentials))
      .url(httpUrl(bucketName, bucketFile.name))

    // Add custom headers
    val r = bucketFile.headers match {
      case Some( headers ) => headers.foldLeft(request) { (r, headerNameValue) => r.withHeaders(headerNameValue) }
      case None => request
    }

    r.withHeaders("X-Amz-acl" -> acl.get.value).put(bucketFile.content)
  }

  /**
   * Lowlevel method to call get on a bucket or a specific file
   *
   * @param bucketName	The name of the bucket
   * @param path		The path that you want to call the get on, default is "" (empty string).
   * 					This is mostly used to retrieve single files
   * @param prefix		A prefix that is most commonly used to list the contents of a 'directory'
   * @param delimiter	A delimiter that is used to distinguish 'directories'
   *
   * @see Bucket.get
   * @see Bucket.list
   */
  def get(bucketName: String, path: Option[String], prefix: Option[String], delimiter: Option[String])(implicit credentials: AwsCredentials): Promise[Response] =
    Aws
      .withSigner(S3Signer(credentials))
      .url(httpUrl(bucketName, path.getOrElse("")))
      .withQueryString(
        (prefix.map("prefix" -> _).toList :::
          delimiter.map("delimiter" -> _).toList): _*)
      .get

  /**
   * Lowlevel method to call delete on a bucket in order to delete a file
   *
   * @param bucketName	The name of the bucket
   * @param path		The path of the file you want to delete
   *
   * @see Bucket.remove
   */
  def delete(bucketName: String, path: String)(implicit credentials: AwsCredentials): Promise[Response] =
    Aws
      .withSigner(S3Signer(credentials))
      .url(httpUrl(bucketName, path))
      .delete

  /**
   * Lowlevel method to create an authenticated url to a specific file
   *
   * @param bucketName	The name of the bucket
   * @param path		The path of the file you want to delete
   * @param expires		Time in seconds since epoch
   *
   * @see Bucket.url
   */
  def url(bucketName: String, path: String, expires: Long)(implicit credentials: AwsCredentials): String = {
    val expireString = expires.toString

    val cannonicalRequest = "GET\n\n\n" + expireString + "\n/" + bucketName + "/" + path
    val s3Signer = S3Signer(credentials)
    val signature = s3Signer.createSignature(cannonicalRequest)

    httpUrl(bucketName, path) +
      "?AWSAccessKeyId=" + credentials.accessKeyId +
      "&Signature=" + s3Signer.urlEncode(signature) +
      "&Expires=" + expireString

  }

  /**
   * Lowlevel method to copy a file on S3
   *
   * @param sourceBucketName		The name of the source bucket
   * @param sourcePath				The path of the file you want to copy
   * @param destinationBucketName	The name of the destination bucket
   * @param destinationPath			The new path of the file you want to copy
   * @param acl						The ACL of the new file
   *
   * @see Bucket.rename
   */
  def putCopy(sourceBucketName: String, sourcePath: String, destinationBucketName: String, destinationPath: String, acl: ACL)(implicit credentials: AwsCredentials): Promise[Response] = {
    val source = "/" + sourceBucketName + "/" + sourcePath

    Aws
      .withSigner(S3Signer(credentials))
      .url(httpUrl(destinationBucketName, destinationPath))
      .withHeaders("X-Amz-acl" -> acl.value)
      .withHeaders("X-Amz-copy-source" -> source)
      .put
  }

}

case class Success()

/**
 * Representation of a bucket
 *
 * @param bucketName	The name of the bucket needed to create a Bucket representation
 * @param delimiter		A delimiter to use for this Bucket instance, default is a / (slash)
 *
 */
case class Bucket(
  name: String,
  delimiter: Option[String] = Some("/"))(implicit val credentials: AwsCredentials) {

  /**
   * Creates an authenticated url for an item with the given name
   *
   * @param itemName	The item for which the url should be created
   * @param expires		The expiration in seconds from now
   */
  def url(itemName: String, expires: Long): String =
    S3.url(name, itemName, ((new Date).getTime / 1000) + expires)

  /**
   * Retrieves a single item with the given name
   *
   * @param itemName	The name of the item you want to retrieve
   */
  def get(itemName: String): Promise[Either[AwsError, BucketFile]] =
    S3.get(name, Some(itemName), None, None) map AwsResponse { (status, response) =>
      BucketFile(itemName, response.header("Content-Type").get, response.ahcResponse.getResponseBodyAsBytes, None)
    }

  /**
   * Lists the contents of the bucket
   */
  def list: Promise[Either[AwsError, Iterable[BucketItem]]] =
    S3.get(name, None, None, delimiter) map listResponse

  /**
   * Lists the contents of a 'directory' in the bucket
   */
  def list(prefix: String): Promise[Either[AwsError, Iterable[BucketItem]]] =
    S3.get(name, None, Some(prefix), delimiter) map listResponse

  /**
   * @see add
   */
  def + = add _
  /**
   * Adds a file to this bucket
   *
   * @param bucketFile	A representation of the file
   */
  def add(bucketFile: BucketFile): Promise[Either[AwsError, Success]] =
    S3.put(name, bucketFile) map successResponse

  /**
   * @see remove
   */
  def - = remove _
  /**
   * Removes a file from this bucket
   *
   * @param itemName	The name of the file that needs to be removed
   */
  def remove(itemName: String): Promise[Either[AwsError, Success]] =
    S3.delete(name, itemName) map successResponse

  /**
   * Creates a new instance of the Bucket with another delimiter
   */
  def withDelimiter(delimiter: String): Bucket = copy(delimiter = Some(delimiter))
  /**
   * Creates a new instance of the Bucket with another delimiter
   */
  def withDelimiter(delimiter: Option[String]): Bucket = copy(delimiter = delimiter)

  /**
   * Allows you to rename a file within this bucket. It will actually do a copy and 
   * a remove.
   * 
   * @param sourceItemName			The old name of the item
   * @param destinationItemName		The new name of the item
   * @param acl						The ACL for the new item, default is PUBLIC_READ
   */
  def rename(sourceItemName: String, destinationItemName: String, acl: ACL = PUBLIC_READ): Promise[Either[AwsError, Success]] =
    (S3.putCopy(name, sourceItemName, name, destinationItemName, acl) map successResponse).flatMap { response =>
      response.fold(
        error => Promise.pure(response),
        success => remove(sourceItemName))
    }

  private def listResponse =
    AwsResponse { (status, response) =>
      val xml = response.xml

      /* files */ (xml \ "Contents").map(n => BucketItem(n \ "Key" text, false)) ++
        /* folders */ (xml \ "CommonPrefixes").map(n => BucketItem(n \ "Prefix" text, true))
    } _

  private def successResponse = AwsResponse { (status, response) => Success() } _
}

/**
 * Representation of an element in a bucket as the result of a call to the list method
 */
case class BucketItem(name: String, isVirtual: Boolean)
/**
 * Representation of a file, used in get and add methods of the bucket
 */
case class BucketFile(name: String, contentType: String, content: Array[Byte], headers: Option[Map[String, String]] = None, acl: Option[ACL] = Some(PUBLIC_READ))

case object PUBLIC_READ extends ACL("public-read")
case object PUBLIC_READ_WRITE extends ACL("public-read-write")
case object AUTHENTICATED_READ extends ACL("authenticated-read")
case object BUCKET_OWNER_READ extends ACL("bucket-owner-read")
case object BUCKET_OWNER_FULL_CONTROL extends ACL("bucket-owner-full-control")

sealed abstract class ACL(val value: String)
