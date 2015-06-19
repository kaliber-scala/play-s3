package fly.play.aws

import play.api.Application
import play.api.PlayException
import play.api.Configuration

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

  implicit def fromApplication(implicit app: Application): AwsCredentials = fromConfiguration("aws", app.configuration)
  def fromApplication(prefix: String)(implicit app: Application): AwsCredentials = fromConfiguration(prefix, app.configuration)

  def fromConfiguration(configuration: Configuration): AwsCredentials = fromConfiguration("aws", configuration)
  def fromConfiguration(prefix: String, configuration: Configuration): AwsCredentials = {
    def error(key:String) = throw new PlayException("Configuration error", "Could not find " + key + " in settings")
    def getOpt(key:String) = configuration getString key
    def get(key:String) = getOpt(key) getOrElse error(key)

    SimpleAwsCredentials(get(s"${prefix}.accessKeyId"), get(s"${prefix}.secretKey"), getOpt(s"${prefix}.token"))
  }
}

case class SimpleAwsCredentials(accessKeyId: String, secretKey: String, token: Option[String] = None) extends AwsCredentials
