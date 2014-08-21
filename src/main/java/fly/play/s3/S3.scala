package fly.play.s3

import scala.concurrent.Future
import fly.play.aws.Aws
import fly.play.aws.auth.AwsCredentials
import play.api.http.ContentTypeOf
import fly.play.aws.auth.Aws4Signer
import fly.play.aws.auth.Signer
import play.api.libs.ws.WS
import play.api.Play.current
import play.api.http.Writeable
import play.api.libs.ws.WSResponse

/**
 * Amazon Simple Storage Service
 */
object S3 {

  @deprecated("The minimal part size is not checked anymore, this variable will be removed", "3.3.1")
  val MINIMAL_PART_SIZE = 5 * 1024 * 1024

  def config = current.configuration

  val regionEndpoints = Map(
      "us-east-1" -> "s3.amazonaws.com",
      "us-west-1" -> "s3-us-west-1.amazonaws.com",
      "us-west-2" -> "s3-us-west-2.amazonaws.com",
      "eu-west-1" -> "s3-eu-west-1.amazonaws.com",
      "ap-southeast-1" -> "s3-ap-southeast-1.amazonaws.com",
      "ap-southeast-2" ->  "s3-ap-southeast-2.amazonaws.com",
      "ap-northeast-1" -> "s3-ap-northeast-1.amazonaws.com",
      "sa-east-1" -> "s3-sa-east-1.amazonaws.com"
  )

  def https = config getBoolean "s3.https" getOrElse false

  def bucketInHostname = config getBoolean "s3.bucketInHostname" getOrElse true

  def host = config getString "s3.host" getOrElse regionEndpoints(region)

  def region = config getString "s3.region" getOrElse "us-east-1"

  def fromConfig(implicit credentials: AwsCredentials) =
    new S3(https, host, region, bucketInHostname)

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
  def url(bucketName: String, path: String, expires: Int)(implicit credentials: AwsCredentials) =
    fromConfig.url(bucketName, path, expires)

}

class S3(val https: Boolean, val host: String, val region: String, val bucketInHostname: Boolean = true)(implicit val credentials: AwsCredentials) {

  lazy val signer = new S3Signer(credentials, region)
  lazy val awsWithSigner = Aws withSigner signer

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

  protected def httpUrl(bucketName: String, path: String) = {
    val protocol = if (https) "https" else "http"

    if (bucketInHostname) s"$protocol://$bucketName.$host/$path"
    else s"$protocol://$host/$bucketName/$path"
  }

  /**
   * Lowlevel method to call put on a bucket in order to store a file
   *
   * @param bucketName	The name of the bucket
   * @param bucketFile	The file that you want to store, if it's acl is None, it's set to PUBLIC_READ
   *
   * @see Bucket.add
   */
  def put(bucketName: String, bucketFile: BucketFile): Future[WSResponse] = {
    val acl = bucketFile.acl getOrElse PUBLIC_READ

    implicit val fileContentType = ContentTypeOf[Array[Byte]](Some(bucketFile.contentType))

    val headers = (bucketFile.headers getOrElse Map.empty).toList

    awsWithSigner
      .url(httpUrl(bucketName, bucketFile.name))
      .withHeaders("X-Amz-acl" -> acl.value :: headers: _*)
      .put(bucketFile.content)
  }

  def putAcl(bucketName: String, sourcePath: String, acl: ACL): Future[WSResponse] = {
    awsWithSigner
      .url(httpUrl(bucketName, sourcePath))
      .withQueryString("acl" -> "")
      .withHeaders("X-Amz-acl" -> acl.value)
      .put
  }

  def getAcl(bucketName: String, sourcePath: String): Future[WSResponse] = {
    awsWithSigner
      .url(httpUrl(bucketName, sourcePath))
      .withQueryString("acl" -> "")
      .get
  }

  /**
   * Lowlevel method to call get on a bucket or a specific file
   *
   * @param bucketName  The name of the bucket
   * @param path        The path that you want to call the get on, default is "" (empty string).
   *                    This is mostly used to retrieve single files
   * @param prefix      A prefix that is most commonly used to list the contents of a 'directory'
   * @param delimiter   A delimiter that is used to distinguish 'directories'
   * @param marker      A marker of the last item retrieved from a subsequent request.  Used to get a bucket
   *                    that has more than 1000 items, as this is the max Amazon will return per request.
   *                    The returns are in lexicographic (alphabetical) order.  See the following:
   *                    http://docs.aws.amazon.com/AmazonS3/latest/API/RESTBucketGET.html
   *
   * @see Bucket.get
   * @see Bucket.list
   */
  def get(bucketName: String, path: Option[String], prefix: Option[String],
    delimiter: Option[String], marker: Option[String]): Future[WSResponse] =

    awsWithSigner
      .url(httpUrl(bucketName, path.getOrElse("")))
      .withQueryString(
        (prefix.map("prefix" -> _).toList :::
          delimiter.map("delimiter" -> _).toList :::
          marker.map("marker" -> _).toList): _*)
      .get

  /**
   * Lowlevel method to call delete on a bucket in order to delete a file
   *
   * @param bucketName	The name of the bucket
   * @param path		The path of the file you want to delete
   *
   * @see Bucket.remove
   */
  def delete(bucketName: String, path: String): Future[WSResponse] =
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
  def url(bucketName: String, path: String, expires: Int): String = {

    val queryString = Map.empty[String, Seq[String]]

    awsWithSigner.signer.signUrl(url(bucketName, path), expires, queryString)
  }

  /**
   * creates an unsigned url to the specified file and bucket
   *
   * @param bucketName the name of the bucket
   * @param path the path of the file we want to create a url for
   */
  def url(bucketName: String, path: String): String =
    httpUrl(bucketName, path)

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
  def putCopy(sourceBucketName: String, sourcePath: String, destinationBucketName: String, destinationPath: String, acl: ACL): Future[WSResponse] = {
    val source = "/" + sourceBucketName + "/" + sourcePath

    awsWithSigner
      .url(httpUrl(destinationBucketName, destinationPath))
      .withHeaders("X-Amz-acl" -> acl.value)
      .withHeaders("X-Amz-copy-source" -> source)
      .put
  }

  /**
   * Lowlevel method to initiate multipart upload
   *
   * @param bucketName	The name of the bucket
   * @param bucketFile	The file that you want to store, if it's acl is None, it's set to PUBLIC_READ
   *
   * @see Bucket.add
   */
  def initiateMultipartUpload(bucketName: String, bucketFile: BucketFile): Future[WSResponse] = {
    require(bucketFile.content.isEmpty, "The given file should not contain content")

    val acl = bucketFile.acl getOrElse PUBLIC_READ

    implicit val fileContentType = ContentTypeOf[String](Some(bucketFile.contentType))

    val headers = (bucketFile.headers getOrElse Map.empty).toList

    awsWithSigner
      .url(httpUrl(bucketName, bucketFile.name))
      .withHeaders("X-Amz-acl" -> acl.value :: headers: _*)
      .withQueryString("uploads" -> "")
      .post("")
  }

  /**
   * Lowlevel method to abort a multipart upload
   *
   * @param bucketName	   The name of the bucket
   * @param uploadTicket   The ticket acquired from initiateMultipartUpload
   *
   * @see initiateMultipartUpload
   */
  def abortMultipartUpload(bucketName: String, uploadTicket: BucketFileUploadTicket): Future[WSResponse] = {

    awsWithSigner
      .url(httpUrl(bucketName, uploadTicket.name))
      .withQueryString(
        "uploadId" -> uploadTicket.uploadId)
      .delete
  }

  /**
   * Lowlevel method to upload a part
   *
   * @param bucketName	   The name of the bucket
   * @param uploadTicket   The ticket acquired from initiateMultipartUpload
   * @param bucketFilePart The part of the file that is uploaded
   *
   * @see initiateMultipartUpload
   */
  def uploadPart(bucketName: String, uploadTicket: BucketFileUploadTicket, bucketFilePart: BucketFilePart): Future[WSResponse] = {
    require(bucketFilePart.partNumber > 0, "The partNumber must be greater than 0")
    require(bucketFilePart.partNumber < 10001, "The partNumber must be lesser than 10001")

    awsWithSigner
      .url(httpUrl(bucketName, uploadTicket.name))
      .withQueryString(
        "partNumber" -> bucketFilePart.partNumber.toString,
        "uploadId" -> uploadTicket.uploadId)
      .put(bucketFilePart.content)
  }

  /**
   * Lowlevel method to complete a multipart upload
   *
   * @param bucketName	      The name of the bucket
   * @param uploadTicket      The ticket acquired from initiateMultipartUpload
   * @param partUploadTickets The tickets acquired from uploadPart
   *
   * @see initiateMultipartUpload
   * @see uploadPart
   */
  def completeMultipartUpload(bucketName: String, uploadTicket: BucketFileUploadTicket, partUploadTickets: Seq[BucketFilePartUploadTicket]): Future[WSResponse] = {
    val body = <CompleteMultipartUpload>{ partUploadTickets.map(_.toXml) }</CompleteMultipartUpload>

    awsWithSigner
      .url(httpUrl(bucketName, uploadTicket.name))
      .withQueryString(
        "uploadId" -> uploadTicket.uploadId)
      .post(body)
  }
}

