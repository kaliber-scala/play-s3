package fly.play.s3

import fly.play.aws.xml.AwsResponse
import java.util.Date
import scala.concurrent.Future
import fly.play.aws.xml.AwsError
import scala.collection.JavaConversions
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Promise
import play.api.libs.ws.Response

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
  s3: S3) extends BucketLike {

  /**
   * Creates an authenticated url for an item with the given name
   *
   * @param itemName	The item for which the url should be created
   * @param expires		The expiration in seconds from now
   */
  def url(itemName: String, expires: Long): String =
    s3.url(name, itemName, ((new Date).getTime / 1000) + expires)

  /**
   * Retrieves a single item with the given name
   *
   * @param itemName	The name of the item you want to retrieve
   */
  def get(itemName: String): Future[BucketFile] =
    s3.get(name, Some(itemName), None, None) map S3Response { (status, response) =>
      val headers = extractHeaders(response)

      BucketFile(itemName,
        headers("Content-Type"),
        response.ahcResponse.getResponseBodyAsBytes,
        None,
        Some(headers))
    }

  /**
   * Lists the contents of the bucket
   */
  def list: Future[Iterable[BucketItem]] =
    s3.get(name, None, None, delimiter) map listResponse

  /**
   * Lists the contents of a 'directory' in the bucket
   */
  def list(prefix: String): Future[Iterable[BucketItem]] =
    s3.get(name, None, Some(prefix), delimiter) map listResponse

  /**
   * Adds a file to this bucket
   *
   * @param bucketFile	A representation of the file
   */
  def add(bucketFile: BucketFile): Future[Unit] =
    s3.put(name, bucketFile) map unitResponse

  /**
   * Removes a file from this bucket
   *
   * @param itemName	The name of the file that needs to be removed
   */
  def remove(itemName: String): Future[Unit] =
    s3.delete(name, itemName) map unitResponse

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
  def rename(sourceItemName: String, destinationItemName: String, acl: ACL = PUBLIC_READ): Future[Unit] = {
    val copyResult = s3.putCopy(name, sourceItemName, name, destinationItemName, acl) map unitResponse
    copyResult.flatMap { response =>
      remove(sourceItemName)
    }
  }

  /**
   * Initiates a multipart upload
   *
   * @param bucketFile	A representation of the file
   *
   * @return The upload id
   */
  def initiateMultipartUpload(bucketFile: BucketFile): Future[BucketFileUploadTicket] = {
    val multipartUpload = s3.initiateMultipartUpload(name, bucketFile)
    multipartUpload map S3Response { (status, response) =>
      val uploadId = (response.xml \ "UploadId").text
      BucketFileUploadTicket(bucketFile.name, uploadId)
    }
  }

  /**
   * Aborts a multipart upload
   *
   * @param uploadTicket	The ticket acquired from initiateMultipartUpload
   *
   */
  def abortMultipartUpload(uploadTicket: BucketFileUploadTicket): Future[Unit] =
    s3.abortMultipartUpload(name, uploadTicket) map unitResponse

  /**
   * Uploads a part in the multipart upload
   *
   * @param uploadTicket    The ticket acquired from initiateMultipartUpload
   * @param bucketFilePart  The part that you want to upload
   */
  def uploadPart(uploadTicket: BucketFileUploadTicket, bucketFilePart: BucketFilePart): Future[BucketFilePartUploadTicket] = {
    val uploadPart = s3.uploadPart(name, uploadTicket, bucketFilePart)

    uploadPart map S3Response { (status, response) =>
      val headers = extractHeaders(response)
      BucketFilePartUploadTicket(bucketFilePart.partNumber, headers("ETag"))
    }
  }

  /**
   * Completes a multipart upload
   *
   * @param uploadTicket      The ticket acquired from initiateMultipartUpload
   * @param partUploadTickets The tickets acquired from uploadPart
   */
  def completeMultipartUpload(uploadTicket: BucketFileUploadTicket, partUploadTickets: Seq[BucketFilePartUploadTicket]): Future[Unit] =
    s3.completeMultipartUpload(name, uploadTicket, partUploadTickets) map unitResponse

  private def extractHeaders(response: Response) = {
    //implicits
    import JavaConversions.mapAsScalaMap
    import JavaConversions.asScalaBuffer

    for {
      (key, value) <- response.ahcResponse.getHeaders.toMap
      if (value.size > 0)
    } yield key -> value.head
  }

  private def listResponse =
    S3Response { (status, response) =>
      val xml = response.xml

      /* files */ (xml \ "Contents").map(n => BucketItem((n \ "Key").text, false)) ++
        /* folders */ (xml \ "CommonPrefixes").map(n => BucketItem((n \ "Prefix").text, true))
    } _

  private def unitResponse = S3Response { (status, response) => } _
}

/**
 * Representation of an element in a bucket as the result of a call to the list method
 */
case class BucketItem(name: String, isVirtual: Boolean)
/**
 * Representation of a file, used in get and add methods of the bucket
 */
case class BucketFile(name: String, contentType: String, content: Array[Byte] = Array.empty, acl: Option[ACL] = None, headers: Option[Map[String, String]] = None)

case class BucketFileUploadTicket(name: String, uploadId: String)

case class BucketFilePart(partNumber: Int, content: Array[Byte])

case class BucketFilePartUploadTicket(partNumber: Int, eTag: String) {
  def toXml = <Part><PartNumber>{ partNumber }</PartNumber><ETag>{ eTag }</ETag></Part>
}

case object PUBLIC_READ extends ACL("public-read")
case object PUBLIC_READ_WRITE extends ACL("public-read-write")
case object AUTHENTICATED_READ extends ACL("authenticated-read")
case object BUCKET_OWNER_READ extends ACL("bucket-owner-read")
case object BUCKET_OWNER_FULL_CONTROL extends ACL("bucket-owner-full-control")

sealed abstract class ACL(val value: String)