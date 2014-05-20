package fly.play.s3

import fly.play.aws.auth.AwsCredentials
import fly.play.aws.auth.Aws4Signer
import play.api.libs.ws.WS
import play.api.http.Writeable

class S3Signer(credentials: AwsCredentials, region: String)
  extends Aws4Signer(credentials, "s3", region) {

  /*
   * Always include the payload header
   */
  val emptyAmzContentSha256 = amzContentSha256(Array.empty)

  override def sign(request: WS.WSRequestHolder, method: String): WS.WSRequestHolder =
    super.sign(request.withHeaders(emptyAmzContentSha256), method)

  override def sign[T](request: WS.WSRequestHolder, method: String, body: T)(implicit wrt: Writeable[T]): WS.WSRequestHolder =
    super.sign(request.withHeaders(amzContentSha256(wrt transform body)), method)
}