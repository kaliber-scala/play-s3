package fly.play.s3

import fly.play.aws.SimpleAwsCredentials
import play.api.test.Helpers.running
import play.api.Play.current

class S3ConfigSpec extends S3SpecSetup {

  sequential

  "S3" should {

    def config = S3Configuration.fromConfig

    "have the correct default value for host" in {
      running(fakeApplication(Map("s3.host" -> null, "s3.region" -> null))) {
        config.host === "s3.amazonaws.com"
      }
    }

    "have the correct default value for https" inApp {
      config.https === true
    }

    "have the correct default value for region" in {
      running(fakeApplication(Map("s3.region" -> null))) {
        config.region === "us-east-1"
      }
    }

    "get the correct value for host from the configuration" in {
      running(fakeApplication(Map("s3.host" -> "testHost"))) {
        config.host === "testHost"
      }
    }

    "get the correct value for https from the configuration" in {
      running(fakeApplication(Map("s3.https" -> false))) {
        config.https === false
      }
    }

    "get the correct default value for region from the configuration" in {
      running(fakeApplication(Map("s3.region" -> "eu-west-1"))) {
        config.region === "eu-west-1"
      }
    }

    "get the correct default value for host if region is set" in {
      running(fakeApplication(Map("s3.region" -> "eu-west-1", "s3.host" -> null))) {
        config.host === "s3-eu-west-1.amazonaws.com"
      }
    }

    "have the correct default value for pathStyleAccess " inApp {
      config.pathStyleAccess === true
    }

    "build a url with the bucket name as part of the host name when pathStyleAccess is false" inApp {
      running(fakeApplication(Map("s3.pathStyleAccess" -> false))) {
     		s3WithCredentials.url("test.bucket", "test") === "https://test.bucket.s3-eu-west-1.amazonaws.com/test"
      }
    }

    "build a url with the bucket name as part of the path when pathStyleAccess is true" inApp {
    	s3WithCredentials.url("test.bucket", "test") === "https://s3-eu-west-1.amazonaws.com/test.bucket/test"
    }

    "return an instance of bucket" inApp {
      S3(testBucketName) must beAnInstanceOf[Bucket]
    }

    "create the correct url" inApp {
      implicit val credentials = SimpleAwsCredentials("test", "test")
      val url = S3.url(testBucketName, "privateREADME.txt", 1234)
      val host = S3Configuration.fromConfig.host

      url must startWith(s"https://$host/$testBucketName/privateREADME.txt")
      url must contain("Expires=1234")
    }

  }
}
