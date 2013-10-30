package fly.play.s3.testUtils

import fly.play.aws.auth.AwsCredentials
import fly.play.s3.BucketLike
import fly.play.s3.S3Like
import scala.collection.mutable
import fly.play.s3.BucketFile

object InMemoryS3 extends S3Like {

  private val buckets = mutable.Map.empty[String, mutable.Map[String, BucketFile]]
  
  private def getFiles(bucketName:String) = 
    buckets.getOrElseUpdate(bucketName, mutable.Map.empty[String, BucketFile])
  
  def apply(bucketName: String)(implicit credentials: AwsCredentials): BucketLike =
    InMemoryBucket(getFiles(bucketName), bucketName)
  def apply(bucketName: String, delimiter: String)(implicit credentials: AwsCredentials): BucketLike =
    InMemoryBucket(getFiles(bucketName), bucketName, Some(delimiter))
}