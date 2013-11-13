package fly.play.s3

trait S3TestUtils { self: TestUtils =>

  def testBucket: Bucket = S3(testBucketName)

}
