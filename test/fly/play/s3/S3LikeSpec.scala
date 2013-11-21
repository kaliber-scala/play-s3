package fly.play.s3

import org.specs2.mutable.Specification
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Success, Failure}

trait S3LikeSpec { self: Specification with TestUtils =>

  def getS3Like: S3Like

  "S3Like" should {
    "return an instance of bucket" inApp {
      getS3Like.apply(testBucketName) must beAnInstanceOf[BucketLike]
    }
  }

  "BucketLike" should {
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
  }
}
