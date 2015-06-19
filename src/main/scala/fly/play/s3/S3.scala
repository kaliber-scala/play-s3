package fly.play.s3

import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.http.Writeable
import play.api.libs.ws.WSResponse
import play.api.Application
import play.api.Configuration
import play.api.libs.ws.WSClient

/**
 * Amazon Simple Storage Service
 */
object S3 {

  def fromApplication(implicit app: Application) =
    fromConfiguration(WS.client, app.configuration)

  def fromConfiguration(client: WSClient, configuration: Configuration) =
    new S3(S3Client(client, S3Configuration fromConfiguration configuration))

  /**
   * Utility method to create a bucket.
   *
   * @see Bucket
   */
  def apply(bucketName: String)(implicit app: Application): Bucket =
    fromApplication.getBucket(bucketName)

  /**
   * Utility method to create a bucket.
   *
   * @see Bucket
   */
  def apply(bucketName: String, delimiter: String)(implicit app: Application): Bucket =
    fromApplication.getBucket(bucketName, delimiter)

  /**
   * Utility method to create an url
   */
  def url(bucketName: String, path: String, expires: Int)(implicit app: Application) =
    fromApplication.url(bucketName, path, expires)

}

class S3(val client:S3Client) {

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

  /**
   * Lowlevel method to call put on a bucket in order to store a file
   *
   * @param bucketName	The name of the bucket
   * @param bucketFile	The file that you want to store, if it's acl is None, it's set to PUBLIC_READ
   *
   * @see Bucket.add
   */
  def put(bucketName: String, bucketFile: BucketFile): Future[WSResponse] = {
    // The comment in the commit was: Don’t use ContentTypeOf in play-ws
    // It's unclear what the underlying reason is
    // https://github.com/playframework/playframework/commit/56ec574eda926496cc736905102f9637c6466132#diff-de5b238615b352ff9143bbb102be70a3
    import play.api.libs.iteratee.Execution.Implicits.trampoline
    implicit val writeable:Writeable[Array[Byte]] = Writeable(identity, Some(bucketFile.contentType))

    val acl = bucketFile.acl getOrElse PUBLIC_READ
    val headers = (bucketFile.headers getOrElse Map.empty).toList

    client
      .resourceRequest(bucketName, bucketFile.name)
      .withHeaders("X-Amz-acl" -> acl.value :: headers: _*)
      .put(bucketFile.content)
  }

  def putAcl(bucketName: String, sourcePath: String, acl: ACL): Future[WSResponse] =
    client
      .resourceRequest(bucketName, sourcePath)
      .withQueryString("acl" -> "")
      .withHeaders("X-Amz-acl" -> acl.value)
      .put("")

  def getAcl(bucketName: String, sourcePath: String): Future[WSResponse] =
    client
      .resourceRequest(bucketName, sourcePath)
      .withQueryString("acl" -> "")
      .get

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

    client
      .resourceRequest(bucketName, path.getOrElse(""))
      .withQueryString(
        (prefix.map("prefix" -> _).toList :::
          delimiter.map("delimiter" -> _).toList :::
          marker.map("marker" -> _).toList): _*)
      .get

  /**
   * Lowlevel method to call head on a specific file
   *
   * @param bucketName  The name of the bucket
   * @param path        The path that you want to call the get on
   *
   * @see Bucket.getHeadersOf
   */
  def head(bucketName: String, path: String): Future[WSResponse] =

    client
      .resourceRequest(bucketName, path)
      .head

  /**
   * Lowlevel method to call delete on a bucket in order to delete a file
   *
   * @param bucketName	The name of the bucket
   * @param path		The path of the file you want to delete
   *
   * @see Bucket.remove
   */
  def delete(bucketName: String, path: String): Future[WSResponse] =
    client
      .resourceRequest(bucketName, path)
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
  def url(bucketName: String, path: String, expires: Int, method: String = "GET"): String = {

    val queryString = Map.empty[String, Seq[String]]

    client.signer.signUrl(method, url(bucketName, path), expires, queryString)
  }

  /**
   * creates an unsigned url to the specified file and bucket
   *
   * @param bucketName the name of the bucket
   * @param path the path of the file we want to create a url for
   */
  def url(bucketName: String, path: String): String =
    client.resourceUrl(bucketName, path)

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
  def putCopy(sourceBucketName: String, sourcePath: String, destinationBucketName: String, destinationPath: String, acl: ACL, headers: Map[String, String] = Map.empty): Future[WSResponse] = {
    val source = "/" + sourceBucketName + "/" + sourcePath

    client
      .resourceRequest(destinationBucketName, destinationPath)
      .withHeaders("X-Amz-acl" -> acl.value)
      .withHeaders("X-Amz-copy-source" -> source)
      .withHeaders(headers.toSeq: _*)
      .put("")
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

    // see comment in put method
    import play.api.libs.iteratee.Execution.Implicits.trampoline
    implicit val writeable:Writeable[Array[Byte]] = Writeable(identity, Some(bucketFile.contentType))

    val acl = bucketFile.acl getOrElse PUBLIC_READ
    val headers = (bucketFile.headers getOrElse Map.empty).toList

    client
      .resourceRequest(bucketName, bucketFile.name)
      .withHeaders("X-Amz-acl" -> acl.value :: headers: _*)
      .withQueryString("uploads" -> "")
      .post(Array.empty[Byte])
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

    client
      .resourceRequest(bucketName, uploadTicket.name)
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

    client
      .resourceRequest(bucketName, uploadTicket.name)
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

    client
      .resourceRequest(bucketName, uploadTicket.name)
      .withQueryString(
        "uploadId" -> uploadTicket.uploadId)
      .post(body)
  }
}
