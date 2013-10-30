package fly.play.s3

import fly.play.aws.auth.AwsCredentials

trait S3Like {

  def apply(bucketName: String)(implicit credentials: AwsCredentials): BucketLike
  def apply(bucketName: String, delimiter: String)(implicit credentials: AwsCredentials): BucketLike
}