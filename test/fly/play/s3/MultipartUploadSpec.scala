package fly.play.s3

import org.specs2.mutable.Specification

object MultipartUploadSpec extends Specification with TestUtils with S3TestUtils {

  sequential

  "Bucket" should {
    "throw an error if the bucket file contains content on initiateMultipartUpload" inApp {
      val bucketFile = BucketFile("test-multipart-file.txt", "text/plain", Array(0))
      testBucket.initiateMultipartUpload(bucketFile) should throwAn[IllegalArgumentException]
    }

    "throw an error if the BucketFilePart has a part number that is not between 1 and 10000" inApp {
      val uploadTicket = BucketFileUploadTicket("test-multipart-file.txt", "")
      val filePart1 = BucketFilePart(0, Array.empty)
      val filePart2 = BucketFilePart(10001, Array.empty)

      testBucket.uploadPart(uploadTicket, filePart1) must throwAn[IllegalArgumentException]
      testBucket.uploadPart(uploadTicket, filePart2) must throwAn[IllegalArgumentException]
    }

    "throw an error if the BucketFilePart is less than 5mb" inApp {
      val uploadTicket = BucketFileUploadTicket("test-multipart-file.txt", "")
      val filePart = BucketFilePart(1, Array.empty)
      testBucket.uploadPart(uploadTicket, filePart) must throwAn[IllegalArgumentException]
    }

    "be able to complete a multipart upload" inApp {
      val fileName = "test-multipart-file.txt"
      val fileContentType = "text/plain"
      val partContent: Array[Byte] = Array.fill(S3.MINIMAL_PART_SIZE)(0)

      val bucketFile = BucketFile(fileName, fileContentType)
      val uploadTicket = await(testBucket.initiateMultipartUpload(bucketFile))

      println("Uploading 5MB to test multipart file upload, this might take some time")
      val filePart = BucketFilePart(1, partContent)
      val partUploadTicket = await(testBucket.uploadPart(uploadTicket, filePart))

      val result = testBucket.completeMultipartUpload(uploadTicket, Seq(partUploadTicket))
      noException(result)

      val file = await(testBucket get fileName)

      file must beLike {
        case BucketFile(name, contentType, content, _, _) =>
          (name === fileName) and
            (contentType must startWith(fileContentType)) and
            (content === partContent)
      }

      noException(testBucket remove fileName)
    }
  }
}
