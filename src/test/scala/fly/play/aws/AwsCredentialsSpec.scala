package fly.play.aws

import org.specs2.mutable.Specification
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

object AwsCredentialsSpec extends Specification {

  implicit val application = new GuiceApplicationBuilder()
    .configure(
      "aws.accessKeyId" -> "testKey",
      "aws.secretKey" -> "testSecret",
      "alt.accessKeyId" -> "altKey",
      "alt.secretKey" -> "altSecret"
    ).build()

  "AwsCredentials" should {

    "retrieve from application" in {
      AwsCredentials.fromApplication must_== AwsCredentials("testKey", "testSecret")
    }

    "load prefixed from application" in {
      AwsCredentials.fromApplication("alt") must_== AwsCredentials("altKey","altSecret")
    }

    "retrieve from configuration" in {
      val config = implicitly[Application].configuration
      AwsCredentials.fromConfiguration(config) must_== AwsCredentials("testKey", "testSecret")
    }

    "load prefixed from configuration" in {
      val config = implicitly[Application].configuration
      AwsCredentials.fromConfiguration("alt", config) must_== AwsCredentials("altKey","altSecret")
    }

    "implement unapply" in {
      val AwsCredentials(a, b, Some(t)) = AwsCredentials("key", "secret", Some("token"))
      a must_== "key"
      b must_== "secret"
      t must_== "token"
    }

    def checkImplicit()(implicit c: AwsCredentials) = c

    "provide an implicit value" in {
      checkImplicit must not beNull
    }

    "override the implicit" in {
      checkImplicit()(AwsCredentials("test", "test")) must_== AwsCredentials("test", "test")
    }
  }
}
