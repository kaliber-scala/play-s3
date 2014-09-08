package fly.play.s3

import fly.play.aws.auth.SimpleAwsCredentials
import play.api.test.Helpers.running
import play.api.Play.current

class S3ConfigSpec extends S3SpecSetup {
  "S3" should {

    "have the correct default value for host" inApp {
      running(fakeApplication(Map("s3.host" -> null, "s3.region" -> null))) {
        s3WithCredentials.host === "s3.amazonaws.com"
      }
    }

    "have the correct default value for https" inApp {
      s3WithCredentials.https === false
    }

    "have the correct default value for region" inApp {
      running(fakeApplication(Map("s3.region" -> null))) {
        s3WithCredentials.region === "us-east-1"
      }
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

    "get the correct default value for region from the configuration" inApp {
      running(fakeApplication(Map("s3.region" -> "eu-west-1"))) {
        s3WithCredentials.region === "eu-west-1"
      }
    }

    "get the correct default value for host if region is set" inApp {
      running(fakeApplication(Map("s3.region" -> "eu-west-1", "s3.host" -> null))) {
        s3WithCredentials.host === "s3-eu-west-1.amazonaws.com"
      }
    }

    "have the correct default value for pathStyleAccess " inApp {
      s3WithCredentials.pathStyleAccess === false
    }

    "build a url with the bucket name as part of the host name when pathStyleAccess is false" in {
      s3WithCredentials.url("test.bucket", "test") === "http://test.bucket.s3.amazonaws.com/test"
    }

    "build a url with the bucket name as part of the path when pathStyleAccess is true" in {
      running(fakeApplication(Map("s3.pathStyleAccess" -> true))) {
        s3WithCredentials.url("test.bucket", "test") === "http://s3.amazonaws.com/test.bucket/test"
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
      val url = S3.url(testBucketName, "privateREADME.txt", 1234)
      val host = S3.host

      url must startWith(s"http://$testBucketName.$host/privateREADME.txt")
      url must contain("Expires=1234")
    }

  }
}
