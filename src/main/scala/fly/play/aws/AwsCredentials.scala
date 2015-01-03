package fly.play.aws

import play.api.Application
import play.api.PlayException

trait AwsCredentials {
  def accessKeyId: String

  def secretKey: String

  def token: Option[String]
}

object AwsCredentials extends ((String, String, Option[String]) => AwsCredentials) {
  def unapply(c: AwsCredentials): Option[(String, String, Option[String])] =
    Option(c) map { c => (c.accessKeyId, c.secretKey, c.token)}

  def apply(accessKeyId: String, secretKey: String, token: Option[String] = None): AwsCredentials =
    SimpleAwsCredentials(accessKeyId, secretKey, token)

  implicit def fromConfiguration(implicit app: Application): AwsCredentials = {
    def error(key:String) = throw new PlayException("Configuration error", "Could not find " + key + " in settings")
    def getOpt(key:String) = app.configuration getString key
    def get(key:String) = getOpt(key) getOrElse error(key)
    
    SimpleAwsCredentials(get("aws.accessKeyId"), get("aws.secretKey"), getOpt("aws.token"))
  }
}

case class SimpleAwsCredentials(accessKeyId: String, secretKey: String, token: Option[String] = None) extends AwsCredentials