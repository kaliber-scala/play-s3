package fly.play.s3

import org.specs2.mutable.Specification
import fly.play.aws.auth.AwsCredentials

object S3SignerSpec extends Specification {

  val fakeCredentials = AwsCredentials("fakeKeyId", "fakeSecret", Some("securityToken"))
  def signer = S3Signer(fakeCredentials, "noHost")

  "S3Signer" should {

    "add the correct headers" in {

      val headers = signer.addHeaders(Map[String, Seq[String]](), "Tue, 22 May 2012 21:13:19 UTC", Some("text/plain"), Some("Md5String"))

      headers must_== Map(
        "Date" -> Seq("Tue, 22 May 2012 21:13:19 UTC"),
        "Content-Type" -> Seq("text/plain"),
        "Content-Md5" -> Seq("Md5String"),
        "X-Amz-Security-Token" -> Seq("securityToken"))

    }

    "create the correct canonical request and signed headers" in {

      "for GET request" in {
        val headers = Map(
          "Date" -> Seq("Tue, 22 May 2012 21:13:19 UTC"),
          "X-Amz-Security-Token" -> Seq("securityToken"))

        val cannonicalRequest = signer.createCannonicalRequest("GET", None, None, "Tue, 22 May 2012 21:13:19 UTC", headers, "/bucketName/testFile")
        cannonicalRequest must_==
          "GET\n" +
          "\n" +
          "\n" +
          "Tue, 22 May 2012 21:13:19 UTC\n" +
          "x-amz-security-token:securityToken\n" +
          "/bucketName/testFile"
      }

      "for POST request" in {
        val headers = Map(
          "Date" -> Seq("Tue, 22 May 2012 21:13:19 UTC"),
          "Content-Type" -> Seq("text/plain"),
          "Content-Md5" -> Seq("Md5String"),
          "X-Amz-Security-Token" -> Seq("securityToken"))

        val cannonicalRequest = signer.createCannonicalRequest("POST", Some("Md5String"), Some("text/plain"), "Tue, 22 May 2012 21:13:19 UTC", headers, "/bucketName/testFile")
        cannonicalRequest must_==
          "POST\n" +
          "Md5String\n" +
          "text/plain\n" +
          "Tue, 22 May 2012 21:13:19 UTC\n" +
          "x-amz-security-token:securityToken\n" +
          "/bucketName/testFile"
      }

    }
  }
}