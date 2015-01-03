package fly.play.s3

import play.api.Application
import fly.play.aws.AwsCredentials

case class S3Configuration(
  credentials: AwsCredentials,
  region: String,
  https: Boolean,
  host: String,
  pathStyleAccess: Boolean
)

object S3Configuration {
  val regionEndpoints = Map(
    "us-east-1" -> "s3.amazonaws.com",
    "us-west-1" -> "s3-us-west-1.amazonaws.com",
    "us-west-2" -> "s3-us-west-2.amazonaws.com",
    "eu-west-1" -> "s3-eu-west-1.amazonaws.com",
    "eu-central-1" -> "s3-eu-central-1.amazonaws.com",
    "ap-southeast-1" -> "s3-ap-southeast-1.amazonaws.com",
    "ap-southeast-2" -> "s3-ap-southeast-2.amazonaws.com",
    "ap-northeast-1" -> "s3-ap-northeast-1.amazonaws.com",
    "sa-east-1" -> "s3-sa-east-1.amazonaws.com")

  def fromConfig(implicit app: Application) = {
    val config = app.configuration

    val region = config getString "s3.region" getOrElse "us-east-1"
    val https = config getBoolean "s3.https" getOrElse false
    val host = config getString "s3.host" getOrElse regionEndpoints(region)
    val pathStyleAccess = config getBoolean "s3.pathStyleAccess" getOrElse false

    S3Configuration(AwsCredentials.fromConfiguration, region, https, host, pathStyleAccess)
  }
}