package fly.play.s3

import java.util.Date
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.mapAsScalaMap
import scala.concurrent.Future
import fly.play.aws.Aws
import fly.play.aws.auth.AwsCredentials
import fly.play.aws.xml.AwsError
import fly.play.aws.xml.AwsResponse
import play.api.http.ContentTypeOf
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.concurrent.Promise
import play.api.libs.ws.Response
import scala.collection.JavaConversions

/**
 * Amazon Simple Storage Service
 */
object S3 {
  val config = play.api.Play.current.configuration
  val use_https = config getBoolean "aws.use_https" getOrElse false
  def getHostname = {
    config getString "aws.hostname" getOrElse "s3.amazonaws.com"
  }
  lazy val defaultS3 = new S3(use_https, getHostname, implicitly[AwsCredentials])

  /**
   * Utility method to create a bucket.
   *
   * @see Bucket
   */
  def apply(bucketName: String)(implicit credentials: AwsCredentials): Bucket = defaultS3.apply(bucketName)
  /**
   * Utility method to create a bucket.
   *
   * @see Bucket
   */
  def apply(bucketName: String, delimiter: String)(implicit credentials: AwsCredentials): Bucket = defaultS3.apply(bucketName, delimiter)
  
}

class S3(use_https: Boolean, val hostname: String, val creds:AwsCredentials) {

  /**
   * Utility method to create a bucket.
   *
   * @see Bucket
   */
  def apply(bucketName: String)(implicit credentials: AwsCredentials): Bucket = Bucket(bucketName, s3=this)(creds)
  /**
   * Utility method to create a bucket.
   *
   * @see Bucket
   */
  def apply(bucketName: String, delimiter: String)(implicit credentials: AwsCredentials): Bucket = Bucket(bucketName, Some(delimiter), this)(creds)

  private def httpUrl(bucketName: String, path: String) =
    {
      val protocol = "http" + { if (use_https) "s" else "" }
      // now build all url
      protocol + "://" + bucketName + "." + hostname + "/" + path
    }

  /**
   * Lowlevel method to call put on a bucket in order to store a file
   *
   * @param bucketName	The name of the bucket
   * @param bucketFile	The file that you want to store, if it's acl is None, it's set to PUBLIC_READ
   *
   * @see Bucket.add
   */
  def put(bucketName: String, bucketFile: BucketFile)(implicit credentials: AwsCredentials): Future[Response] = {
    val acl = bucketFile.acl getOrElse PUBLIC_READ

    implicit val fileContentType = ContentTypeOf[Array[Byte]](Some(bucketFile.contentType))

    val headers = (bucketFile.headers getOrElse Map.empty).toList

    Aws
      .withSigner(S3Signer(credentials))
      .url(httpUrl(bucketName, bucketFile.name))
      .withHeaders("X-Amz-acl" -> acl.value :: headers: _*)
      .put(bucketFile.content)
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
  def get(bucketName: String, path: Option[String], prefix: Option[String], delimiter: Option[String])(implicit credentials: AwsCredentials): Future[Response] =
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
  def delete(bucketName: String, path: String)(implicit credentials: AwsCredentials): Future[Response] =
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
  def putCopy(sourceBucketName: String, sourcePath: String, destinationBucketName: String, destinationPath: String, acl: ACL)(implicit credentials: AwsCredentials): Future[Response] = {
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
  delimiter: Option[String] = Some("/"),
  s3:S3 = S3.defaultS3)(implicit val credentials: AwsCredentials) {

  /**
   * Creates an authenticated url for an item with the given name
   *
   * @param itemName	The item for which the url should be created
   * @param expires		The expiration in seconds from now
   */
  def url(itemName: String, expires: Long): String =
    s3.url(name, itemName, ((new Date).getTime / 1000) + expires)(credentials)

  /**
   * Retrieves a single item with the given name
   *
   * @param itemName	The name of the item you want to retrieve
   */
  def get(itemName: String): Future[Either[AwsError, BucketFile]] =
    s3.get(name, Some(itemName), None, None)(credentials) map AwsResponse { (status, response) =>
      //implicits
      import JavaConversions.mapAsScalaMap
      import JavaConversions.asScalaBuffer

      val headers =
        for {
          (key, value) <- response.ahcResponse.getHeaders.toMap
          if (value.size > 0)
        } yield key -> value.head

      BucketFile(itemName,
        headers("Content-Type"),
        response.ahcResponse.getResponseBodyAsBytes,
        None,
        Some(headers))
    }

  /**
   * Lists the contents of the bucket
   */
  def list: Future[Either[AwsError, Iterable[BucketItem]]] =
    s3.get(name, None, None, delimiter)(credentials) map listResponse

  /**
   * Lists the contents of a 'directory' in the bucket
   */
  def list(prefix: String): Future[Either[AwsError, Iterable[BucketItem]]] =
    s3.get(name, None, Some(prefix), delimiter)(credentials) map listResponse

  /**
   * @see add
   */
  def + = add _
  /**
   * Adds a file to this bucket
   *
   * @param bucketFile	A representation of the file
   */
  def add(bucketFile: BucketFile): Future[Either[AwsError, Success]] =
    s3.put(name, bucketFile)(credentials) map successResponse

  /**
   * @see remove
   */
  def - = remove _
  /**
   * Removes a file from this bucket
   *
   * @param itemName	The name of the file that needs to be removed
   */
  def remove(itemName: String): Future[Either[AwsError, Success]] =
    s3.delete(name, itemName)(credentials) map successResponse

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
  def rename(sourceItemName: String, destinationItemName: String, acl: ACL = PUBLIC_READ): Future[Either[AwsError, Success]] =
    (s3.putCopy(name, sourceItemName, name, destinationItemName, acl)(credentials) map successResponse).flatMap { response =>
      response.fold(
        error => Promise.pure(response),
        success => remove(sourceItemName))
    }

  private def listResponse =
    AwsResponse { (status, response) =>
      val xml = response.xml

      /* files */ (xml \ "Contents").map(n => BucketItem((n \ "Key").text, false)) ++
        /* folders */ (xml \ "CommonPrefixes").map(n => BucketItem((n \ "Prefix").text, true))
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
case class BucketFile(name: String, contentType: String, content: Array[Byte], acl: Option[ACL] = None, headers: Option[Map[String, String]] = None)

case object PUBLIC_READ extends ACL("public-read")
case object PUBLIC_READ_WRITE extends ACL("public-read-write")
case object AUTHENTICATED_READ extends ACL("authenticated-read")
case object BUCKET_OWNER_READ extends ACL("bucket-owner-read")
case object BUCKET_OWNER_FULL_CONTROL extends ACL("bucket-owner-full-control")

sealed abstract class ACL(val value: String)
