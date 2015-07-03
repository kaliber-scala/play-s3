package fly.play.s3

import java.util.Date
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.mapAsScalaMap
import scala.concurrent.Future
import scala.xml.Elem
import fly.play.aws.policy.PolicyBuilder
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.ws.WSResponse
import fly.play.aws.acl.ACLList
import fly.play.aws.policy.Condition

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
  s3: S3) {

  /**
   * Creates an authenticated url for an item with the given name
   *
   * @param itemName	The item for which the url should be created
   * @param expires		The expiration in seconds from now
   */
  def url(itemName: String, expires: Int): String =
    s3.url(name, itemName, expires)

  /**
   * Creates an authenticated url for an item with the given name and method
   *
   * @param itemName	The item for which the url should be created
   * @param expires		The expiration in seconds from now
   */
  def url(itemName: String, expires: Int, method: String): String =
    s3.url(name, itemName, expires, method)

  /**
   * Creates an unsigned url for the given item name
   *
   * @param itemName  The item for which the url should be created
   */
  def url(itemName: String, method: String = "GET"): String =
    s3.url(name, itemName)

  /**
   * Utility method to create a policy builder for this bucket
   *
   * @param expires		The date this policy expires
   */
  def uploadPolicy(expiration: Date): PolicyBuilder =
    PolicyBuilder(expiration)(s3.client.signer).withConditions(Condition.string("bucket") eq name)

  /**
   * Retrieves a single item with the given name
   *
   * @param itemName	The name of the item you want to retrieve
   */
  def get(itemName: String): Future[BucketFile] =
    s3.get(name, Some(itemName), None, None, None) map S3Response { (status, response) =>
      val headers = extractHeaders(response)

      BucketFile(itemName,
        headers("Content-Type"),
        response.underlying[com.ning.http.client.Response].getResponseBodyAsBytes,
        None,
        Some(headers))
    }

  /**
   * Retrueves the headers of a single item.
   *
   * @param itemName  The name of the item you want to receive the headers from
   */
  def getHeadersOf(itemName: String): Future[Map[String, Seq[String]]] =
    s3.head(name, itemName) map (_.allHeaders)

  /**
   * Lists the contents of the bucket
   */
  def list: Future[Iterable[BucketItem]] =
    list(None)

  /**
   * Lists the contents of a 'directory' in the bucket
   */
  def list(prefix: String): Future[Iterable[BucketItem]] =
    list(Some(prefix))

  private def list(prefix: Option[String]): Future[Iterable[BucketItem]] = {

    def listWithMarker(marker: Option[String], accum: Seq[BucketItem]): Future[Iterable[BucketItem]] =
      s3.get(name, None, prefix, delimiter, marker) map
        listResponse flatMap {
          case ListResponse(elems, None) => Future.successful(accum ++ elems)
          case ListResponse(elems, next) => listWithMarker(next, accum ++ elems)
        }

    listWithMarker(None, Seq.empty)
  }

  /**
   * @see add
   */
  def + = add _
  /**
   * Adds a file to this bucket
   *
   * @param bucketFile	A representation of the file
   */
  def add(bucketFile: BucketFile): Future[Unit] =
    s3.put(name, bucketFile) map unitResponse

  /**
   * @see remove
   */
  def - = remove _
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
   * Note that renaming a file removes the `server-side-encryption`, `storage-class`,
   * and `website-redirect-location` metadata.
   *
   * @param sourceItemName			The old name of the item
   * @param destinationItemName		The new name of the item
   * @param acl						The ACL for the new item, default is PUBLIC_READ
   */
  def rename(sourceItemName: String, destinationItemName: String, acl: ACL = PUBLIC_READ, headers: Map[String, String] = Map.empty): Future[Unit] = {
    val copyResult = s3.putCopy(name, sourceItemName, name, destinationItemName, acl, headers) map unitResponse
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

  /**
   * Updates the ACL of given item
   *
   * @param itemName	The name of file that needs to be updated
   * @param acl			The ACL
   */
  def updateAcl(itemName: String, acl: ACL): Future[Unit] =
    s3.putAcl(name, itemName, acl) map unitResponse

  /**
   * Retrieves the ACL
   *
   * @param itemName	The name of the file that you want to retrieve the ACL for
   */
  def getAcl(itemName: String): Future[ACLList] =
    s3.getAcl(name, itemName) map aclListResponse

  private def extractHeaders(response: WSResponse) = {
    for {
      (key, value) <- response.underlying[com.ning.http.client.Response].getHeaders.toMap
      if (value.size > 0)
    } yield key -> value.head
  }

  private case class ListResponse(items: Seq[BucketItem], nextMarker: Option[String])

  private def listResponse =
    S3Response { (status, response) =>
      val xml = response.xml

      val isTruncated = (xml \ "IsTruncated").text == "true"

      var items = /* files */ (xml \ "Contents").map(n => BucketItem((n \ "Key").text, false))
      if (delimiter.isDefined)
        items ++= /* folders */ (xml \ "CommonPrefixes").map(n => BucketItem((n \ "Prefix").text, true))

      val nextMarker =
        if (isTruncated)
          if (delimiter.isDefined)
            Some((xml \ "NextMarker").text)
          else items.lastOption.map(_.name)
        else None

      ListResponse(items, nextMarker)
    } _

  private def aclListResponse =
    S3Response { (status, response) =>
      val xml = response.xml

      ACLList((xml \ "AccessControlList").head.asInstanceOf[Elem])
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

case object PRIVATE extends ACL("private")
case object PUBLIC_READ extends ACL("public-read")
case object PUBLIC_READ_WRITE extends ACL("public-read-write")
case object AUTHENTICATED_READ extends ACL("authenticated-read")
case object BUCKET_OWNER_READ extends ACL("bucket-owner-read")
case object BUCKET_OWNER_FULL_CONTROL extends ACL("bucket-owner-full-control")

sealed abstract class ACL(val value: String)