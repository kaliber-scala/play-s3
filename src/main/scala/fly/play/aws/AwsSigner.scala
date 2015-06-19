package fly.play.aws

import play.api.libs.ws.WSRequest
import fly.play.aws.policy.PolicyBuilder
import fly.play.aws.policy.AwsPolicy
import scala.concurrent.Future

trait AwsSigner {
  def sign(request:WSRequest, method:String, body:Array[Byte]):WSRequest

  def signUrl(method:String, url: String, expiresIn: Int, queryString: Map[String, Seq[String]] = Map.empty): String

  def createPolicy(policyBuilder:PolicyBuilder):AwsPolicy
}