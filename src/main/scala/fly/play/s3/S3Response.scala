package fly.play.s3

import fly.play.aws.AwsResponse
import play.api.libs.ws.WSResponse

object S3Response {
  def apply[T](converter: (Int, WSResponse) => T)(response: WSResponse): T =
    AwsResponse(converter)(response) match {
      case Left(awsError) => throw S3Exception(awsError)
      case Right(t) => t
    }
}