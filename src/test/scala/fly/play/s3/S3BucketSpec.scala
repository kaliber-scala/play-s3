package fly.play.s3

import java.util.Date
import fly.play.aws.AwsUrlEncoder
import fly.play.aws.acl.{ CanonicalUser, FULL_CONTROL, Grant, Group, READ }
import fly.play.aws.policy.Condition
import fly.play.s3.upload.{ Form, FormElement }
import play.api.http.HeaderNames.{ CONTENT_TYPE, LOCATION }
import play.api.libs.json.Json
import play.api.libs.json.JsArray
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.test.Helpers.running
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{ Failure, Success }
import utils.MultipartFormData

class S3BucketSpec extends S3SpecSetup {
  sequential

  implicit val application = fakeApplication()

  "S3 should return an instance of bucket" inApp {
    S3(testBucketName) must beAnInstanceOf[Bucket]
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
        case Failure(err)                                 => ko("Unexpected failure: " + err)
        case Success(x)                                   => ko("Error was expected, no error received: " + x)
      }
    }

    "be able to add a file" inApp {

      val result = testBucket +
        BucketFile("README.txt", "text/plain", """
          This is a bucket used for testing the S3 module of play
        """.getBytes)

      noException(result)
    }

    "with the correct mime type" inApp {

      val result = S3.fromApplication.get(testBucket.name, Some("README.txt"), None, None, None)
      val value = await(result)

      value.header(CONTENT_TYPE) must_== Some("text/plain")
    }

    "be able to check if it exists" inApp {

      val result = testBucket get "README.txt"
      val value = await(result)

      value match {
        case BucketFile("README.txt", _, _, _, _) => success
        case f                                    => failure("Wrong file returned: " + f)
      }
    }

    "be able add a file with a prefix" inApp {

      val result = testBucket +
        BucketFile("testPrefix/README.txt", "text/plain", """
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
        case f                               => failure("Wrong file returned: " + f)
      }
      seq(1) match {
        case BucketItem("testPrefix/", true) => success
        case f                               => failure("Wrong file returned: " + f)
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

    "be able to create a url for methods other than GET" inApp {
      val getUrl1 = testBucket.url("test.txt", 86400)
      val getUrl2 = testBucket.url("test.txt", 86400)
      val postUrl = testBucket.url("test.txt", 86400, method = "POST")

      getUrl1 === getUrl2
      postUrl !== getUrl1
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

    "be able to copy (clone) a file" inApp {

      val result = testBucket clone ("private2README.txt", "copyTest.txt", AUTHENTICATED_READ)
      noException(result)
    }

    "<cleanup of the file>" inApp {
      noException(testBucket remove "copyTest.txt")
    }

    "be able to retrieve the headers of a file" inApp {
      val fileName = "headerTest.txt"

      val headers = Map("x-amz-meta-custom-header" -> "test-header")
      await(testBucket add BucketFile(fileName, "text/plain", "test".getBytes, headers = Some(headers)))

      val retrievedHeaders = await(testBucket getHeadersOf fileName)

      retrievedHeaders.get("x-amz-meta-custom-header") === Some(Seq("test-header"))
    }

    "<cleanup of the file>" inApp {
      noException(testBucket remove "headerTest.txt")
    }

    "be able to rename a file while passing new headers" inApp {
      val fileName = "testHeaders.txt"
      val newFileName = "testHeaders2.txt"

      // This header will not be kept by amazon
      val headers = Map("x-amz-website-redirect-location" -> "/test")

      await(testBucket add BucketFile(fileName, "text/plain", "test".getBytes, headers = Some(headers)))

      await(testBucket rename (
        fileName,
        newFileName,
        headers = Map("x-amz-website-redirect-location" -> "/test2")
      ))

      val result = await(testBucket getHeadersOf newFileName)

      result.get("x-amz-website-redirect-location") === Some(Seq("/test2"))
    }

     "<cleanup of the file>" inApp {
      noException(testBucket remove "testHeaders2.txt")
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
        case f => ko("Wrong file returned: " + f)
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
      val fileContentType = "custom/type"
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
        testBucket.uploadPolicy(new Date())
          .withConditions(
            Condition.key eq "privateREADME.txt",
            Condition.acl eq PUBLIC_READ,
            Condition.contentLengthRange from 0 to 10000,
            CONTENT_TYPE -> "image/jpeg")

      val policy = policyBuilder.json

      val expectedConditions = Json.arr(
        Json.obj("bucket" -> testBucketName),
        Json.obj("key" -> "privateREADME.txt"),
        Json.obj("acl" -> "public-read"),
        Json.arr("content-length-range", 0, 10000),
        Json.obj(CONTENT_TYPE -> "image/jpeg"))
      (policy \ "conditions").as[JsArray] === expectedConditions
    }

    "be able to supply an uploadPolicy that can be used to alow browser upload" in {
      running(fakeApplication(Map("play.ws.followRedirects" -> false))) {

        val `1 minute from now` = System.currentTimeMillis + (1 * 60 * 1000)

        import fly.play.aws.policy.Condition._
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
            .toPolicy

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

      def uploadListAndRemoveFileWithName(prefix: String, name: String) = {
        await(testBucket + BucketFile(AwsUrlEncoder.encodePath(prefix + name), "text/plain", "test".getBytes))

        await(testBucket.list(prefix)) must beLike {
          case Seq(BucketItem(itemName, false)) => itemName === (prefix + name)
        }

        noException(testBucket - AwsUrlEncoder.encodePath(prefix + name))
      }

      uploadListAndRemoveFileWithName("sample/", "test file.txt")
      uploadListAndRemoveFileWithName("sample/", "test&;-file.txt")
      uploadListAndRemoveFileWithName("sample/", "test & file.txt")
      uploadListAndRemoveFileWithName("sample/", "test+&+file.txt")
    }

    "be able to list contents greater than 1000 items" inApp {
      def batched[T, R](amount: Int, s: Seq[T])(f: T => Future[R]): Future[Seq[R]] =
        s.grouped(amount)
          .foldLeft(Future.successful(Seq.empty[R])) { (acc, elems) =>
            acc.flatMap { results =>
              Future.sequence(elems.map(f))
                .map(results ++ _)
                .map { r => print('-'); r }
            }
          }

      val sizeBeforeAddingItems = await(testBucket.list).size
      val amount = 1100

      def fileName(number: Int) = s"README-$number.txt"

      print("creating files: ")
      val createdFileNames = {
        val exampleFile = BucketFile(fileName(0), "text/plain", "Many files example".getBytes)
        val exampleStored = testBucket + exampleFile
        exampleStored.flatMap { _ =>

          batched(40, 1 to amount) { number =>
            val name = fileName(number)

            S3.fromApplication
              .putCopy(testBucketName, exampleFile.name, testBucketName, name, PUBLIC_READ)
              .map { _ =>
                name
              }
          }
        }
      }
      val fileNames = await(createdFileNames)
      print("\n")

      await(testBucket.remove(fileName(0)))
      val filesAvailable1 = await(testBucket.list)

      val bucketWithoutDelimiter = testBucket.withDelimiter(None)

      val filesAvailable2 = await(bucketWithoutDelimiter.list)

      print("removing files: ")
      val removedFiles = batched(40, fileNames)(testBucket.remove)
      await(removedFiles)
      print("\n")

      filesAvailable1.size === sizeBeforeAddingItems + amount
      filesAvailable2.size === sizeBeforeAddingItems + amount
    }

    "correctly rename files into nested directories" >> {

      val source1 = "renameTest1.txt"
      val source2 = "renameTest2.txt"
      val target1 = "d1/" + source1
      val target2 = "d2/d3/" + source2

      "single directory" inApp {
        await(testBucket add BucketFile(source1, "text/plain", "test rename 1".getBytes, Some(AUTHENTICATED_READ)))

        noException(testBucket.rename(source1, target1, AUTHENTICATED_READ))
      }

      "nested directory" inApp {
        await(testBucket add BucketFile(source2, "text/plain", "test rename 2".getBytes, Some(AUTHENTICATED_READ)))

        noException(testBucket.rename(source2, target2, AUTHENTICATED_READ))
      }

      "<cleanup of the file>" inApp {
        noException(testBucket remove target1)
        noException(testBucket remove target2)
      }
    }
  }
}
