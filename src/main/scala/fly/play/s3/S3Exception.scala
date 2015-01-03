package fly.play.s3

import scala.xml.Elem
import fly.play.aws.AwsError

case class S3Exception(status: Int, code: String, message: String, originalXml: Option[Elem]) extends RuntimeException(
  s"""|Problem accessing S3. Status $status, code $code, message '$message'
	  |Original xml:
      |$originalXml""".stripMargin) {

}

object S3Exception {
  def apply(awsError:AwsError):S3Exception = 
    S3Exception(awsError.status, awsError.code, awsError.code, awsError.originalXml)
}