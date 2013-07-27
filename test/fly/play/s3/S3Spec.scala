package fly.play.s3

import java.io.File
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import fly.play.aws.auth.SimpleAwsCredentials
import fly.play.aws.xml.AwsError
import play.api.http.HeaderNames
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import org.specs2.execute.AsResult
import org.specs2.specification.Example
import play.api.test.FakeApplication
import play.api.test.Helpers._
import fly.play.aws.auth.AwsCredentials
import scala.util.Success
import scala.util.Failure
import scala.concurrent.duration.Duration

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
      S3.url("s3playlibrary.rhinofly.net", "privateREADME.txt", 1343845068) must_==
        "http://s3playlibrary.rhinofly.net.s3.amazonaws.com/privateREADME.txt?AWSAccessKeyId=AKIAIJJLEMC6OSI2DN2A&Signature=jkbj7%2ByalcC%2Fw%2BKxtMXLIn7b%2Frc%3D&Expires=1343845068"
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

      value match {
        case Failure(S3Exception(404, "NoSuchKey", _, _)) => success
        case Failure(err) => failure("Unexpected failure: " + err)
        case Success(x) => failure("Error was expected, no error received: " + x)
      }
    }

    "be able to add a file" inApp {

      val result = testBucket + BucketFile("README.txt", "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes)

      Await.result(result, Duration.Inf) must not(throwA[Throwable])
    }

    "with the correct mime type" inApp {

      val result = s3WithCredentials.get(testBucket.name, Some("README.txt"), None, None)
      val value = Await.result(result, Duration.Inf)

      value.header(HeaderNames.CONTENT_TYPE) must_== Some("text/plain")
    }

    "be able to check if it exists" inApp {

      val result = testBucket get "README.txt"
      val value = Await.result(result, Duration.Inf)

      value match {
        case BucketFile("README.txt", _, _, _, _) => success
        case f => failure("Wrong file returned: " + f)
      }
    }

    "be able add a file with a prefix" inApp {

      val result = testBucket + BucketFile("testPrefix/README.txt", "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes)

      Await.result(result, Duration.Inf) must not(throwA[Throwable])
    }

    "list should be iterable" inApp {

      testBucket.list must beAnInstanceOf[Future[Iterable[BucketItem]]]
    }

    "list should have a size of 2" inApp {

      val result = testBucket.list
      val value = Await.result(result, Duration.Inf)

      value.size === 2
    }

    "list should have the correct contents" inApp {
      val result = testBucket.list
      val value = Await.result(result, Duration.Inf)

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
      val value = Await.result(result, Duration.Inf)
      value.toSeq(0) match {
        case BucketItem("testPrefix/README.txt", false) => success
        case f => failure("Wrong file returned: " + f)
      }
    }

    "be able to delete a file" inApp {

      val result = testBucket - "testPrefix/README.txt"

      Await.result(result, Duration.Inf) must not(throwA[Throwable])
    }

    var url = ""

    "be able to add a file with private ACL and create a url for it" inApp {

      val fileName = "privateREADME.txt"
      url = testBucket.url(fileName, 86400)
      val result = testBucket + BucketFile(fileName, "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes, Some(AUTHENTICATED_READ), None)

      Await.result(result, Duration.Inf) must not(throwA[Throwable])
    }

    "be able to retrieve the private file using the generated url" inApp {

      val result = WS.url(url).get

      val value = Await.result(result, Duration.Inf)
      value.status must_== 200
    }

    "be able to rename a file" inApp {

      val result = testBucket rename ("privateREADME.txt", "private2README.txt", AUTHENTICATED_READ)
      Await.result(result, Duration.Inf) must not(throwA[Throwable])
    }

    "be able to delete the renamed private file" inApp {

      val result = testBucket remove "private2README.txt"
      Await.result(result, Duration.Inf) must not(throwA[Throwable])
    }

    "be able to add a file with custom headers" inApp {

      val result = testBucket + BucketFile("headerTest.txt", "text/plain", """
		        This file is used for testing custome headers
		        """.getBytes, None, Some(Map("x-amz-meta-testheader" -> "testHeaderValue")))

      Await.result(result, Duration.Inf) must not(throwA[Throwable])
    }

    "be able to retrieve a file with custom headers" inApp {

      val result = testBucket.get("headerTest.txt")
      val value = Await.result(result, Duration.Inf)
      value match {
        case BucketFile("headerTest.txt", _, _, _, Some(headers)) => (headers get "x-amz-meta-testheader") must_== Some("testHeaderValue")
        case f => failure("Wrong file returned: " + f)
      }
    }

    "be able to delete the file with custom headers" inApp {
      
      val result = testBucket remove "headerTest.txt"
      Await.result(result, Duration.Inf) must not(throwA[Throwable])
    }
  }
}
