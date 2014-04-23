package fly.play.s3

import java.io.File
import java.lang.IllegalArgumentException
import java.util.Date
import scala.concurrent.Await
import scala.concurrent.Awaitable
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success
import org.specs2.execute.AsResult
import org.specs2.mutable.Specification
import org.specs2.specification.Example
import fly.play.aws.auth.SimpleAwsCredentials
import fly.play.s3.acl.CanonicalUser
import fly.play.s3.acl.FULL_CONTROL
import fly.play.s3.acl.Grant
import fly.play.s3.acl.Group
import fly.play.s3.acl.READ
import fly.play.s3.upload.Condition
import fly.play.s3.upload.Form
import fly.play.s3.upload.FormElement
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.HeaderNames.LOCATION
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import play.api.test.Helpers.running
import utils.MultipartFormData
import java.net.URLEncoder

class S3Spec extends Specification {

  sequential

  val testBucketName = "s3playlibrary.rhinofly.net"

  def fakeApplication(additionalConfiguration: Map[String, _ <: Any] = Map.empty) =
    FakeApplication(new File("./test"), additionalConfiguration = additionalConfiguration)

  implicit class InAppExample(s: String) {
    def inApp[T: AsResult](r: => T): Example =
      s in running(fakeApplication()) {
        r
      }
  }

  def s3WithCredentials = S3.fromConfig

  def await[T](a: Awaitable[T]): T =
    Await.result(a, Duration.Inf)

  def noException[T](a: Awaitable[T]) =
    await(a) must not(throwA[Throwable])

  "S3" should {

    "have the correct default value for host" inApp {
      s3WithCredentials.host === "s3.amazonaws.com"
    }

    "have the correct default value for https" inApp {
      s3WithCredentials.https === false
    }

    "get the correct value for host from the configuration" in {
      running(fakeApplication(Map("s3.host" -> "testHost"))) {
        s3WithCredentials.host === "testHost"
      }
    }

    "get the correct value for https from the configuration" in {
      running(fakeApplication(Map("s3.https" -> true))) {
        s3WithCredentials.https === true
      }
    }

    "return an instance of bucket" inApp {
      S3(testBucketName) must beAnInstanceOf[Bucket]
    }

    "return an instance of bucket with different credentials" inApp {
      implicit val awsCredentials = SimpleAwsCredentials("test", "test")
      val bucket = S3(testBucketName)

      bucket.s3.credentials must_== awsCredentials
    }

    "create the correct url" inApp {
      implicit val credentials = SimpleAwsCredentials("test", "test")
      S3.url("s3playlibrary.rhinofly.net", "privateREADME.txt", 1343845068) must_==
        "http://s3playlibrary.rhinofly.net.s3.amazonaws.com/privateREADME.txt?AWSAccessKeyId=test&Signature=FCUeFIgwLzBdtutUF4mvxARPOMA%3D&Expires=1343845068"
    }

  }

  "Bucket" should {
    def testBucket = S3(testBucketName)

    "by default have / as delimiter" inApp {
      testBucket.delimiter must_== Some("/")
    }

    "should be able to change delimiter" inApp {
      var bucket = testBucket
      bucket = bucket withDelimiter Some("-")
      bucket.delimiter must_== Some("-")

      bucket = bucket withDelimiter None
      bucket.delimiter must_== None

      bucket = bucket withDelimiter "/"
      bucket.delimiter must_== Some("/")
    }

    "give an error if we request an element that does not exist" inApp {

      val result = testBucket.get("nonExistingElement")
      val value = Await.ready(result, Duration.Inf).value.get

      value must beLike {
        case Failure(S3Exception(404, "NoSuchKey", _, _)) => ok
        case Failure(err) => failure("Unexpected failure: " + err)
        case Success(x) => failure("Error was expected, no error received: " + x)
      }
    }

    "be able to add a file" inApp {

      val result = testBucket + BucketFile("README.txt", "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes)

      noException(result)
    }

    "with the correct mime type" inApp {

      val result = s3WithCredentials.get(testBucket.name, Some("README.txt"), None, None, None, None)
      val value = await(result)

      value.header(CONTENT_TYPE) must_== Some("text/plain")
    }

    "be able to check if it exists" inApp {

      val result = testBucket get "README.txt"
      val value = await(result)

      value match {
        case BucketFile("README.txt", _, _, _, _) => success
        case f => failure("Wrong file returned: " + f)
      }
    }

    "be able add a file with a prefix" inApp {

      val result = testBucket + BucketFile("testPrefix/README.txt", "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes)

      noException(result)
    }

    "list should be iterable" inApp {

      testBucket.list must beAnInstanceOf[Future[Iterable[BucketItem]]]
    }

    "list should have a size of 2" inApp {

      val result = testBucket.list
      val value = await(result)

      value.size === 2
    }

    "list should have the correct contents" inApp {
      val result = testBucket.list
      val value = await(result)

      val seq = value.toSeq
      seq(0) match {
        case BucketItem("README.txt", false) => success
        case f => failure("Wrong file returned: " + f)
      }
      seq(1) match {
        case BucketItem("testPrefix/", true) => success
        case f => failure("Wrong file returned: " + f)
      }
    }

    "list with prefix should return the correct contents" inApp {

      val result = testBucket.list("testPrefix/")
      val value = await(result)
      value.toSeq(0) match {
        case BucketItem("testPrefix/README.txt", false) => success
        case f => failure("Wrong file returned: " + f)
      }
    }

    "be able to delete a file" inApp {

      val result = testBucket - "testPrefix/README.txt"

      noException(result)
    }

    var url = ""

    "be able to add a file with private ACL and create a url for it" inApp {

      val fileName = "privateREADME.txt"
      url = testBucket.url(fileName, 86400)
      val result = testBucket + BucketFile(fileName, "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes, Some(AUTHENTICATED_READ), None)

      noException(result)
    }

    "be able to retrieve the private file using the generated url" inApp {

      val result = WS.url(url).get

      val value = await(result)
      value.status must_== 200
    }

    "be able to rename a file" inApp {

      val result = testBucket rename ("privateREADME.txt", "private2README.txt", AUTHENTICATED_READ)
      noException(result)
    }

    "be able to change the file's ACL" inApp {
      val result = testBucket.updateAcl("private2README.txt", PUBLIC_READ)
      noException(result)
    }

    "be able to check if the file's ACL has been updated" inApp {
      val result = testBucket.getAcl("private2README.txt")
      val value = await(result)

      value must beLike {
        case Seq(
          Grant(FULL_CONTROL, CanonicalUser(_, _)),
          Grant(READ, Group(uri))) =>
          uri must endWith("AllUsers")
      }
    }

    "be able to delete the renamed private file" inApp {

      val result = testBucket remove "private2README.txt"
      noException(result)
    }

    "be able to add a file with custom headers" inApp {

      val result = testBucket + BucketFile("headerTest.txt", "text/plain", """
		        This file is used for testing custome headers
		        """.getBytes, None, Some(Map("x-amz-meta-testheader" -> "testHeaderValue")))

      noException(result)
    }

    "be able to retrieve a file with custom headers" inApp {

      val result = testBucket.get("headerTest.txt")
      val value = await(result)
      value match {
        case BucketFile("headerTest.txt", _, _, _, Some(headers)) => (headers get "x-amz-meta-testheader") must_== Some("testHeaderValue")
        case f => failure("Wrong file returned: " + f)
      }
    }

    "be able to delete the file with custom headers" inApp {

      val result = testBucket remove "headerTest.txt"
      noException(result)
    }

    "throw an error if the bucket file contains content on initiateMultipartUpload" inApp {
      val bucketFile = BucketFile("test-multipart-file.txt", "text/plain", Array(0))
      testBucket.initiateMultipartUpload(bucketFile) should throwAn[IllegalArgumentException]
    }

    "throw an error if the BucketFilePart has a part number that is not between 1 and 10000" inApp {
      val uploadTicket = BucketFileUploadTicket("test-multipart-file.txt", "")
      val filePart1 = BucketFilePart(0, Array.empty)
      val filePart2 = BucketFilePart(10001, Array.empty)

      testBucket.uploadPart(uploadTicket, filePart1) must throwAn[IllegalArgumentException]
      testBucket.uploadPart(uploadTicket, filePart2) must throwAn[IllegalArgumentException]
    }

    "be able to complete a multipart upload" inApp {
      val fileName = "test-multipart-file.txt"
      val fileContentType = "text/plain"
      val partContent: Array[Byte] = Array.fill(100)(0)

      val bucketFile = BucketFile(fileName, fileContentType)
      val uploadTicket = await(testBucket.initiateMultipartUpload(bucketFile))

      val filePart = BucketFilePart(1, partContent)
      val partUploadTicket = await(testBucket.uploadPart(uploadTicket, filePart))

      val result = testBucket.completeMultipartUpload(uploadTicket, Seq(partUploadTicket))
      noException(result)

      val file = await(testBucket get fileName)

      file must beLike {
        case BucketFile(name, contentType, content, _, _) =>
          (name === fileName) and
            (contentType must startWith(fileContentType)) and
            (content === partContent)
      }

      noException(testBucket remove fileName)
    }

    "be able to supply an uploadPolicy" inApp {
      val policyBuilder =
        testBucket.uploadPolicy(new Date)
          .withConditions(
            Condition.key eq "privateREADME.txt",
            Condition.acl eq PUBLIC_READ,
            Condition.contentLengthRange from 0 to 10000,
            CONTENT_TYPE -> "image/jpeg")

      val policy = policyBuilder.json

      val expectedConditions = Json.arr(
        Json.obj("bucket" -> "s3playlibrary.rhinofly.net"),
        Json.obj("key" -> "privateREADME.txt"),
        Json.obj("acl" -> "public-read"),
        Json.arr("content-length-range", 0, 10000),
        Json.obj(CONTENT_TYPE -> "image/jpeg"))
      (policy \ "conditions") === expectedConditions
    }

    "be able to supply an uploadPolicy that can be used to alow browser upload" in {
      running(fakeApplication(Map("ws.followRedirects" -> false))) {

        val `1 minute from now` = System.currentTimeMillis + (1 * 60 * 1000)

        import Condition._
        val key = Condition.key
        val expectedFileName = "test/file.html"
        val expectedRedirectUrl = "http://fakehost:9000"
        val expectedTags = "one,two"
        val expectedContentType = "text/html"

        val policy =
          testBucket.uploadPolicy(new Date(`1 minute from now`))
            .withConditions(
              key startsWith "test/",
              acl eq PUBLIC_READ,
              successActionRedirect eq expectedRedirectUrl,
              header(CONTENT_TYPE) startsWith "text/",
              meta("tag").any)

        // provide user input
        val formFieldsFromPolicy =
          Form(policy).fields
            .map {
              case FormElement("key", _, true) =>
                "key" -> expectedFileName
              case FormElement("x-amz-meta-tag", _, true) =>
                "x-amz-meta-tag" -> expectedTags
              case FormElement(CONTENT_TYPE, _, true) =>
                CONTENT_TYPE -> expectedContentType
              case FormElement(name, value, false) =>
                name -> value
            }

        val expectedContent = "test text"
        // file should be the last field
        val formFields = formFieldsFromPolicy :+ ("file" -> expectedContent)

        val data = MultipartFormData(formFields, "asdfghjkl123")

        val response = await(WS.url(testBucket.url("")).post(data.body))

        response.status === 303
        response.header(LOCATION).get must startWith(expectedRedirectUrl)

        val bucketFile = await(testBucket.get(expectedFileName))

        bucketFile must beLike {
          case BucketFile(name, contentType, content, None, headers) =>
            name === expectedFileName
            contentType === expectedContentType
            new String(content) === expectedContent
            headers.get("x-amz-meta-tag") === expectedTags
        }

        noException(testBucket remove expectedFileName)
      }

    }

    "be able to add and delete files with 'weird' names" inApp {

      def uploadListAndRemoveFileWithName(prefix:String, name: String) = {
        await(testBucket + BucketFile(URLEncoder.encode(prefix + name, "UTF-8"), "text/plain", "test".getBytes))

        await(testBucket.list(prefix)) must beLike {
          case Seq(BucketItem(itemName, false)) => itemName === (prefix + name)
        }

        await(testBucket - URLEncoder.encode(prefix + name, "UTF-8"))

        success
      }

      uploadListAndRemoveFileWithName("sample/", "test file.txt")
      uploadListAndRemoveFileWithName("sample/", "test&;-file.txt")
      uploadListAndRemoveFileWithName("sample/", "test & file.txt")
      uploadListAndRemoveFileWithName("sample/", "test+&+file.txt")
    }

    "list should be able to list contents greater than 1000 items" inApp {
      val curr = await(testBucket.list).size
      val ITEMS = 1100
      def populateFiles(items: Integer) = {
        1 to items foreach { number =>
          testBucket + BucketFile(s"README-$number.txt", "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes)
        }
      }
      populateFiles(ITEMS)
      val result = testBucket.list
      val value = await(result)
      value.size === curr + ITEMS
    }

  }
}
