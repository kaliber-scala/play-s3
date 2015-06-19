package fly.play.aws

import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import play.api.Play.current
import play.api.test.FakeApplication
import play.api.test.Helpers._
import play.api.Application

object AwsCredentialsSpec extends Specification {

  def app[T](t: => T): T = running(
    FakeApplication(
      additionalConfiguration = Map(
        "aws.accessKeyId" -> "testKey",
        "aws.secretKey" -> "testSecret",
        "alt.accessKeyId" -> "altKey",
        "alt.secretKey" -> "altSecret"
      )
    )
  ) { t }

  "AwsCredentials" should {

    "retrieve from application" in app {
      AwsCredentials.fromApplication must_== AwsCredentials("testKey", "testSecret")
    }

    "load prefixed from application" in app {
      AwsCredentials.fromApplication("alt") must_== AwsCredentials("altKey","altSecret")
    }

    "retrieve from configuration" in app {
      val config = implicitly[Application].configuration
      AwsCredentials.fromConfiguration(config) must_== AwsCredentials("testKey", "testSecret")
    }

    "load prefixed from configuration" in app {
      val config = implicitly[Application].configuration
      AwsCredentials.fromConfiguration("alt", config) must_== AwsCredentials("altKey","altSecret")
    }

    "implement unapply" in app {
      val AwsCredentials(a, b, Some(t)) = AwsCredentials("key", "secret", Some("token"))
      a must_== "key"
      b must_== "secret"
      t must_== "token"
    }

    def checkImplicit()(implicit c: AwsCredentials) = c

    "provide an implicit value" in app {
      checkImplicit must not beNull
    }

    "override the implicit" in app {
      checkImplicit()(AwsCredentials("test", "test")) must_== AwsCredentials("test", "test")
    }
  }
}
