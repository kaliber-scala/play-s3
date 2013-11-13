package fly.play.s3

import org.specs2.specification.Example
import org.specs2.execute.AsResult
import org.specs2.mutable.Specification
import play.api.test.Helpers._
import play.api.test.FakeApplication
import java.io.File
import scala.concurrent.Awaitable
import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait TestUtils { self:Specification =>
  
  val testBucketName = "s3playlibrary.rhinofly.net"

  def testBucket: BucketLike

  def fakeApplication(additionalConfiguration: Map[String, _ <: Any] = Map.empty) =
    FakeApplication(new File("./test"), additionalConfiguration = additionalConfiguration)
  
  implicit class InAppExample(s: String) {
    def inApp[T: AsResult](r: => T): Example =
      s in running(fakeApplication()) {
        r
      }
  }
  
  def await[T](a: Awaitable[T]): T =
    Await.result(a, Duration.Inf)

  def noException[T](a: Awaitable[T]) =
    await(a) must not(throwA[Throwable])
}
