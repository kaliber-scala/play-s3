package fly.play.aws

import play.api.libs.ws.WSRequestHolder
import fly.play.aws.policy.PolicyBuilder
import fly.play.aws.policy.AwsPolicy
import scala.concurrent.Future

trait AwsSigner {
  def sign(request:WSRequestHolder, method:String, body:Array[Byte]):WSRequestHolder
  
  def signUrl(method:String, url: String, expiresIn: Int, queryString: Map[String, Seq[String]] = Map.empty): String
  
  def createPolicy(policyBuilder:PolicyBuilder):AwsPolicy
}