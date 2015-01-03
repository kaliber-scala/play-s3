package fly.play.aws

import play.api.libs.ws.WSResponse

object AwsResponse {
  def apply[T](converter: (Int, WSResponse) => T)(response: WSResponse): Either[AwsError, T] =
    response.status match {
      case status if status >= 200 && status < 300 => Right(converter(status, response))
      case status => Left(
          if (response.body.isEmpty) AwsError(status, "unknown error", "no body", None) else AwsError(response.status, response.xml))
    }
}