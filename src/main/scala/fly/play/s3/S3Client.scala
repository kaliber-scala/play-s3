package fly.play.s3

import play.api.libs.ws.WSClient
import play.api.libs.ws.WSRequest
import fly.play.aws.AwsRequestHolder
import fly.play.aws.AwsSigner
import scala.concurrent.ExecutionContext

class S3Client(private val wsClient: WSClient, val signer: AwsSigner, val configuration: S3Configuration) {

  def resourceRequest(bucketName: String, path: String)(implicit executionContext: ExecutionContext): WSRequest = {
    val url = resourceUrl(bucketName, path)
    new AwsRequestHolder(wsClient.url(url).withFollowRedirects(true), signer, executionContext)
  }

  def resourceUrl(bucketName: String, path: String) = {
    val S3Configuration(_, _, https, host, pathStyleAccess) = configuration
    val protocol = if (https) "https" else "http"

    if (pathStyleAccess) s"$protocol://$host/$bucketName/$path"
    else s"$protocol://$bucketName.$host/$path"
  }
}

object S3Client {
  def apply(wsClient: WSClient, configuration: S3Configuration) = {
    val signer = new S3Signer(configuration.credentials, configuration.region)
    new S3Client(wsClient, signer, configuration)
  }
}
