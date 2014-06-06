package fly.play.s3

import play.api.libs.ws.Response
import fly.play.aws.xml.AwsResponse

object S3Response {
  def apply[T](converter: (Int, Response) => T)(response: Response): T =
    AwsResponse(converter)(response) match {
      case Left(awsError) => throw S3Exception(awsError)
      case Right(t) => t
    }
}