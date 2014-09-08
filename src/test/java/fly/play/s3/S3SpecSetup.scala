package fly.play.s3

import java.io.File

import scala.concurrent.{Await, Awaitable}
import scala.concurrent.duration.DurationInt
import org.specs2.execute.AsResult
import org.specs2.mutable.Specification
import org.specs2.specification.Example
import org.specs2.time.NoTimeConversions
import play.api.Play.current
import play.api.test.FakeApplication
import play.api.test.Helpers.running
import play.api.Application

trait S3SpecSetup extends Specification with NoTimeConversions {
  def testBucketName(implicit app: Application) =
    app.configuration.getString("testBucketName").getOrElse(sys.error("Could not find testBucketName in configuration"))

  def fakeApplication(additionalConfiguration: Map[String, _ <: Any] = Map.empty) =
    FakeApplication(new File("./src/test"), additionalConfiguration = additionalConfiguration)

  implicit class InAppExample(s: String) {
    def inApp[T: AsResult](r: => T): Example =
      s in running(fakeApplication()) {
        r
      }
  }

  def s3WithCredentials = S3.fromConfig

  def await[T](a: Awaitable[T]): T =
    Await.result(a, 120.seconds)

  def noException[T](a: Awaitable[T]) =
    await(a) must not(throwA[Throwable])
}
