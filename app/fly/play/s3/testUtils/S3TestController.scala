package fly.play.s3.testUtils

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import fly.play.s3.S3Exception

class S3TestController(bucketName: String) extends Controller {

  def getInMemoryFile(file: String) = Action.async {

    val bucket = InMemoryS3(bucketName).asInstanceOf[InMemoryBucket]
    val bucketFile = bucket fromUrl file

    bucketFile
      .map { bucketFile =>
        val headers = bucketFile.headers.map(_.toSeq).getOrElse(Seq.empty)

        Ok(bucketFile.content)
          .as(bucketFile.contentType)
          .withHeaders(headers: _*)
      }
      .recover {
        case S3Exception(404, _, _, _) => NotFound
        case S3Exception(403, _, _, _) => Forbidden
      }
  }

}