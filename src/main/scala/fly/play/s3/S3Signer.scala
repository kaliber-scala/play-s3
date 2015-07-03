package fly.play.s3

import fly.play.aws.AwsCredentials
import fly.play.aws.Aws4Signer
import play.api.libs.ws.WSRequestHolder

class S3Signer(credentials: AwsCredentials, region: String)
  extends Aws4Signer(credentials, "s3", region) {

  // allways include the content payload header
  override def sign(request: WSRequestHolder, method: String, body: Array[Byte]): WSRequestHolder =
    super.sign(request.withHeaders(amzContentSha256(body)), method, body)

}