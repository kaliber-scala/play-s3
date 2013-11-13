package fly.play.s3

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import fly.play.s3.testUtils.InMemoryS3

class InMemoryS3Spec extends Specification with TestUtils with NoTimeConversions with S3LikeSpec {
  def getS3Like = InMemoryS3

  override def testBucket = getS3Like(testBucketName)
}
