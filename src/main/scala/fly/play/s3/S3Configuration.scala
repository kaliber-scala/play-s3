package fly.play.s3

import play.api.Application
import fly.play.aws.AwsCredentials
import play.api.Configuration

case class S3Configuration(
  credentials: AwsCredentials,
  region: String,
  https: Boolean,
  host: String,
  pathStyleAccess: Boolean
)

object S3Configuration {
  val regionEndpoints = Map(
    "ca-central-1" -> "s3.ca-central-1.amazonaws.com",
    "us-east-1" -> "s3.amazonaws.com",
    "us-east-2" -> "s3.us-east-2.amazonaws.com",
    "us-west-1" -> "s3-us-west-1.amazonaws.com",
    "us-west-2" -> "s3-us-west-2.amazonaws.com",
    "sa-east-1" -> "s3.sa-east-1.amazonaws.com",
    "eu-central-1" -> "s3-eu-central-1.amazonaws.com",
    "eu-west-1" -> "s3-eu-west-1.amazonaws.com",
    "eu-west-2" -> "s3.eu-west-2.amazonaws.com",
    "eu-west-3" -> "s3.eu-west-3.amazonaws.com",
    "ap-south-1" -> "s3.ap-south-1.amazonaws.com",
    "ap-southeast-1" -> "s3-ap-southeast-1.amazonaws.com",
    "ap-southeast-2" -> "s3-ap-southeast-2.amazonaws.com",
    "ap-northeast-1" -> "s3-ap-northeast-1.amazonaws.com",
    "ap-northeast-2" -> "s3.ap-northeast-2.amazonaws.com",
    "ap-northeast-3" -> "s3.ap-northeast-3.amazonaws.com",
    "cn-north-1" -> "s3.cn-north-1.amazonaws.com.cn",
    "cn-northwest-1" -> "s3.cn-northwest-1.amazonaws.com.cn",
    "sa-east-1" -> "s3-sa-east-1.amazonaws.com"
  )

  def fromApplication(implicit app: Application) = fromConfiguration(app.configuration)

  def fromConfiguration(configuration: Configuration) = {

    val region = configuration.getOptional[String]("s3.region") getOrElse "us-east-1"
    val https = configuration.getOptional[Boolean]("s3.https") getOrElse true
    val host = configuration.getOptional[String]("s3.host") getOrElse regionEndpoints(region)
    val pathStyleAccess = configuration.getOptional[Boolean]("s3.pathStyleAccess") getOrElse true

    S3Configuration(AwsCredentials fromConfiguration configuration, region, https, host, pathStyleAccess)
  }
}
