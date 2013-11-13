package fly.play.s3

import scala.concurrent.Future
import org.specs2.mutable.Specification
import fly.play.aws.auth.SimpleAwsCredentials
import play.api.http.HeaderNames
import play.api.libs.ws.WS
import play.api.test.Helpers._
import scala.concurrent.duration._
import org.specs2.time.NoTimeConversions

class S3Spec extends Specification with TestUtils with NoTimeConversions with S3LikeSpec {

  sequential

  def s3WithCredentials: S3 = S3.fromConfig


  override def testBucket = S3(testBucketName)

  override def getS3Like = S3

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

    "return an instance of bucket with different credentials" inApp {
      implicit val awsCredentials = SimpleAwsCredentials("test", "test")
      val bucket = S3(testBucketName)

      bucket.s3.credentials must_== awsCredentials
    }

    "create the correct url" inApp {
      S3.url("s3playlibrary.rhinofly.net", "privateREADME.txt", 1343845068) must_==
        "http://s3playlibrary.rhinofly.net.s3.amazonaws.com/privateREADME.txt?AWSAccessKeyId=AKIAIJJLEMC6OSI2DN2A&Signature=jkbj7%2ByalcC%2Fw%2BKxtMXLIn7b%2Frc%3D&Expires=1343845068"
    }
  }

  "Bucket" should {

    "be able to add a file" inApp {

      val result = testBucket + BucketFile("README.txt", "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes)

      noException(result)
    }

    "with the correct mime type" inApp {

      val result = s3WithCredentials.get(testBucket.name, Some("README.txt"), None, None)
      val value = await(result)

      value.header(HeaderNames.CONTENT_TYPE) must_== Some("text/plain")
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
    val fileName = "privateREADME.txt"

    "be able to add a file with private ACL and create a url for it" inApp {

      url = testBucket.url(fileName, 24.hours.toSeconds)
      val result = testBucket + BucketFile(fileName, "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes, Some(AUTHENTICATED_READ), None)

      noException(result)
    }

    "be able to create a url with fast expiration time and get a timeout" inApp {

      val url = testBucket.url(fileName, -60)

      val result = WS.url(url).get

      val value = await(result)
      value.status must_== 403
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
  }
}
