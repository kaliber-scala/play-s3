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

class S3Spec extends Specification with Before with DeactivatedTimeConversions {

  sequential

  def before = play.api.Play.start(FakeApplication(new File("./test")))

  val testBucketName = "s3playlibrary.rhinofly.net"

  "S3" should {
    "return an instance of bucket" in {
      S3(testBucketName) must beAnInstanceOf[Bucket]
    }
    "return an instance of bucket with different credentials" in {
      implicit val awsCredentials = SimpleAwsCredentials("test", "test")
      val bucket = S3(testBucketName)

      bucket.credentials must_== awsCredentials
    }

    "create the correct url" in {
      S3.url("s3playlibrary.rhinofly.net", "privateREADME.txt", 1343845068) must_==
        "http://s3playlibrary.rhinofly.net.s3.amazonaws.com/privateREADME.txt?AWSAccessKeyId=AKIAIJJLEMC6OSI2DN2A&Signature=jkbj7%2ByalcC%2Fw%2BKxtMXLIn7b%2Frc%3D&Expires=1343845068"
    }
  }

  "Bucket" should {
    var bucket = S3(testBucketName)

    "by default have / as delimiter" in {
      bucket.delimiter must_== Some("/")
    }

    "should be able to change delimiter" in {
      bucket = bucket withDelimiter "-"
      bucket.delimiter must_== Some("-")

      bucket = bucket withDelimiter None
      bucket.delimiter must_== None

      bucket = bucket withDelimiter "/"
      bucket.delimiter must_== Some("/")
    }

    "give an error if we request an element that does not exist" in {
      val result = bucket.get("nonExistingElement")
      val value = Await.result(result, 10 seconds)
      value.fold(
        _ match {
          case AwsError(404, "NoSuchKey", _, _) => success
          case x => failure("Unexpected error: " + x)
        },
        { x => failure("Error was expected, no error received: " + x) })
    }

    "be able to add a file" in {
      val result = bucket + BucketFile("README.txt", "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes)
      val value = Await.result(result, 10 seconds)
      value.fold({ e => failure(e.toString) }, { s => success })
    }

    "with the correct mime type" in {
      val result = S3.get(bucket.name, Some("README.txt"), None, None)
      val value = Await.result(result, 10 seconds)

      value.header(HeaderNames.CONTENT_TYPE) must_== Some("text/plain")
    }

    "be able to check if it exists" in {
      val result = bucket.get("README.txt")
      val value = Await.result(result, 10 seconds)
      value.fold(
        { e => failure(e.toString) },
        { f =>
          f match {
            case BucketFile("README.txt", _, _, _, _) => success
            case f => failure("Wrong file returned: " + f)
          }
        })

    }

    "be able add a file with a prefix" in {
      val result = bucket + BucketFile("testPrefix/README.txt", "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes)
      val value = Await.result(result, 10 seconds)
      value.fold({ e => failure(e.toString) }, { s => success })
    }

    "list should be iterable" in {
      bucket.list must beAnInstanceOf[Future[Iterable[BucketItem]]]
    }

    "list should have a size of 2" in {
      val result = bucket.list
      val value = Await.result(result, 10 seconds)
      value.fold(
        { e => failure(e.toString) },
        { i => i.size must_== 2 })
    }

    "list should have the correct contents" in {
      val result = bucket.list
      val value = Await.result(result, 10 seconds)
      value.fold(
        { e => failure(e.toString) },
        { i =>
          val seq = i.toSeq
          seq(0) match {
            case BucketItem("README.txt", false) => success
            case f => failure("Wrong file returned: " + f)
          }
          seq(1) match {
            case BucketItem("testPrefix/", true) => success
            case f => failure("Wrong file returned: " + f)
          }

        })
    }

    "list with prefix should return the correct contents" in {
      val result = bucket.list("testPrefix/")
      val value = Await.result(result, 10 seconds)
      value.fold(
        { e => failure(e.toString) },
        { i =>
          i.toSeq(0) match {
            case BucketItem("testPrefix/README.txt", false) => success
            case f => failure("Wrong file returned: " + f)
          }

        })
    }

    "be able to delete a file" in {
      val result = bucket - "testPrefix/README.txt"
      val value = Await.result(result, 10 seconds)
      value.fold({ e => failure(e.toString) }, { s => success })
    }

    var url = ""

    "be able to add a file with private ACL and create a url for it" in {
      val fileName = "privateREADME.txt"
      url = bucket.url(fileName, 86400)
      val result = bucket + BucketFile(fileName, "text/plain", """
		        This is a bucket used for testing the S3 module of play
		        """.getBytes, Some(AUTHENTICATED_READ), None)
      val value = Await.result(result, 10 seconds)
      value.fold({ e => failure(e.toString) }, { s => success })
    }

    "be able to retrieve the private file using the generated url" in {
      val result = WS.url(url).get
      val value = Await.result(result, 10 seconds)
      value.status must_== 200
    }

    "be able to rename a file" in {
      val result = bucket rename ("privateREADME.txt", "private2README.txt", AUTHENTICATED_READ)
      val value = Await.result(result, 10 seconds)
      value.fold({ e => failure(e.toString) }, { s => success })
    }

    "be able to delete the renamed private file" in {
      val result = bucket remove "private2README.txt"
      val value = Await.result(result, 10 seconds)
      value.fold({ e => failure(e.toString) }, { s => success })
    }

    "be able to add a file with custom headers" in {
      val result = bucket + BucketFile("headerTest.txt", "text/plain", """
		        This file is used for testing custome headers
		        """.getBytes, None, Some(Map("x-amz-meta-testheader" -> "testHeaderValue")))
      val value = Await.result(result, 10 seconds)
      value.fold({ e => failure(e.toString) }, { s => success })
    }

    "be able to retrieve a file with custom headers" in {
      val result = bucket.get("headerTest.txt")
      val value = Await.result(result, 10 seconds)
      value.fold(
        { e => failure(e.toString) },
        { f =>
          f match {
            case BucketFile("headerTest.txt", _, _, _, Some(headers)) => (headers get "x-amz-meta-testheader") must_== Some("testHeaderValue")
            case f => failure("Wrong file returned: " + f)
          }
        })
    }

    "be able to delete the file with custom headers" in {
      val result = bucket remove "headerTest.txt"
      val value = Await.result(result, 10 seconds)
      value.fold({ e => failure(e.toString) }, { s => success })
    }
  }
}
