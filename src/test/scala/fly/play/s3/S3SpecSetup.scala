package fly.play.s3

import scala.concurrent.{Await, Awaitable}
import scala.concurrent.duration.DurationInt
import org.specs2.execute.AsResult
import org.specs2.mutable.Specification
import org.specs2.specification.core.Fragment
import play.api.test.Helpers.running
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient

trait S3SpecSetup extends Specification {
  def testBucketName(implicit app: Application) =
    app.configuration.getOptional[String]("testBucketName").getOrElse(sys.error("Could not find testBucketName in configuration"))


  def fakeApplication(additionalConfiguration: Map[String, _ <: Any] = Map.empty) =
    new GuiceApplicationBuilder().configure(additionalConfiguration).build()

  implicit class InAppExample(s: String) {
    def inApp[T: AsResult](r: => T): Fragment =
      s in running(fakeApplication()) {
        r
      }
  }

  def await[T](a: Awaitable[T]): T =
    Await.result(a, 120.seconds)

  def noException[T](a: Awaitable[T]) =
    await(a) must not(throwA[Throwable])
}
