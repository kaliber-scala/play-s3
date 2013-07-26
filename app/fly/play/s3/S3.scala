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
  
  def config = play.api.Play.current.configuration
  
  def https = config getBoolean "s3.https" getOrElse false
  def host = config getString "s3.host" getOrElse "s3.amazonaws.com" 
  
  def fromConfig(implicit credentials:AwsCredentials) = 
    new S3(https, host)
  
  /**
   * Utility method to create a bucket.
   *
   * @see Bucket
   */
  def apply(bucketName: String)(implicit credentials: AwsCredentials): Bucket = 
    fromConfig.getBucket(bucketName)
    
  /**
   * Utility method to create a bucket.
   *
   * @see Bucket
   */
  def apply(bucketName: String, delimiter: String)(implicit credentials: AwsCredentials): Bucket =
    fromConfig.getBucket(bucketName, delimiter)

    
  /**
   * Utility method to create an url
   */
  def url(bucketName: String, path: String, expires: Long)(implicit credentials: AwsCredentials) =
  	fromConfig.url(bucketName, path, expires)
  
}

class S3(val https:Boolean, val host:String)(implicit val credentials: AwsCredentials) {

  lazy val s3Signer = S3Signer(credentials, host)

  lazy val awsWithSigner = Aws withSigner s3Signer
  
  /**
   * Utility method to create a bucket.
   *
   * @see Bucket
   */
  def getBucket(bucketName: String): Bucket = Bucket(bucketName, s3 = this)

  /**
   * Utility method to create a bucket.
   *
   * @see Bucket
   */
  def getBucket(bucketName: String, delimiter: String): Bucket = 
    Bucket(bucketName, Some(delimiter), this)

  private def httpUrl(bucketName: String, path: String) = {
    
      var protocol = "http"
      if (https) protocol += "s" 
      // now build all url
      protocol + "://" + bucketName + "." + host + "/" + path
    }

  /**
   * Lowlevel method to call put on a bucket in order to store a file
   *
   * @param bucketName	The name of the bucket
   * @param bucketFile	The file that you want to store, if it's acl is None, it's set to PUBLIC_READ
   *
   * @see Bucket.add
   */
  def put(bucketName: String, bucketFile: BucketFile): Future[Response] = {
    val acl = bucketFile.acl getOrElse PUBLIC_READ

    implicit val fileContentType = ContentTypeOf[Array[Byte]](Some(bucketFile.contentType))

    val headers = (bucketFile.headers getOrElse Map.empty).toList

    awsWithSigner
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
  def get(bucketName: String, path: Option[String], prefix: Option[String], delimiter: Option[String]): Future[Response] =
    awsWithSigner
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
  def delete(bucketName: String, path: String): Future[Response] =
    awsWithSigner
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
  def url(bucketName: String, path: String, expires: Long): String = {
    val expireString = expires.toString

    val cannonicalRequest = "GET\n\n\n" + expireString + "\n/" + bucketName + "/" + path
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
  def putCopy(sourceBucketName: String, sourcePath: String, destinationBucketName: String, destinationPath: String, acl: ACL): Future[Response] = {
    val source = "/" + sourceBucketName + "/" + sourcePath

    awsWithSigner
      .url(httpUrl(destinationBucketName, destinationPath))
      .withHeaders("X-Amz-acl" -> acl.value)
      .withHeaders("X-Amz-copy-source" -> source)
      .put
  }

}

