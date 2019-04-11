package fly.play.s3

import fly.play.aws.SimpleAwsCredentials
import play.api.Configuration
import play.api.libs.ws.WSClient

class S3ConfigSpec extends S3SpecSetup {

  sequential

  val baseConfig = Map("aws.accessKeyId" -> "", "aws.secretKey" -> "")

  implicit val application = fakeApplication()

  "S3" should {

    "have the correct default value for host" in {
      config.host === "s3.amazonaws.com"
    }

    "have the correct default value for https" in {
      config.https === true
    }

    "have the correct default value for pathStyleAccess " in {
      config.pathStyleAccess === true
    }

    "have the correct default value for region" in {
      config.region === "us-east-1"
    }

    "get the correct value for host from the configuration" in {
      configWith("s3.host" -> "testHost").host === "testHost"
    }

    "get the correct value for https from the configuration" in {
      configWith("s3.https" -> "false").https === false
    }

    "get the correct default value for path style access from the configuration" in {
      configWith("s3.pathStyleAccess" -> "false").pathStyleAccess === false
    }

    "get the correct value for region from the configuration" in {
      configWith("s3.region" -> "eu-west-1").region === "eu-west-1"
    }

    "get the correct default value for host if region is set" in {
      configWith("s3.region" -> "eu-west-1").host === "s3-eu-west-1.amazonaws.com"
    }

    "build a url with the bucket name as part of the host name when pathStyleAccess is false" in {
       s3With("s3.pathStyleAccess" -> "false", "s3.region" -> "eu-west-1")
        .url("test.bucket", "test") === "https://test.bucket.s3-eu-west-1.amazonaws.com/test"
    }

    "build a url with the bucket name as part of the path when pathStyleAccess is true" in {
      s3With("s3.region" -> "eu-west-1")
        .url("test.bucket", "test") === "https://s3-eu-west-1.amazonaws.com/test.bucket/test"
    }

    "create the correct url" in {
      implicit val credentials = SimpleAwsCredentials("test", "test")
      val url = S3.url(testBucketName, "privateREADME.txt", 1234)
      val host = S3Configuration.fromApplication.host

      url must startWith(s"https://$host/$testBucketName/privateREADME.txt")
      url must contain("Expires=1234")
    }
  }

  def config = configWith()

  def configWith(config: (String, String) *) = {
    // We prefer to use additional configuration with:
    //   "s3.host" -> null
    // but that does not work anymore
    //
    // https://github.com/playframework/playframework/issues/4829

    S3Configuration.fromConfiguration(Configuration.from(baseConfig ++ config.toMap))
  }

  def s3With(config: (String, String) *) = {
    val fakeWSClient = new WSClient {
      def underlying[T] = ???
      def url(url: String) = ???
      def close() = ???
    }
    new S3(S3Client(fakeWSClient, configWith(config: _*)))
  }

}
