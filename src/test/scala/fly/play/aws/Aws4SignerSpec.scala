package fly.play.aws

import java.net.URLEncoder
import java.util.{Calendar, Date, TimeZone}

import akka.util.ByteString
import fly.play.s3.S3SpecSetup
import play.api.libs.ws.{BodyWritable, InMemoryBody, WSRequest}
import play.api.test.WsTestClient

import scala.collection.mutable
import scala.language.reflectiveCalls

object Aws4SignerSpec extends S3SpecSetup {

  "Aws4Signer" should {

    "example1" >> {
      val expectedCannonicalRequest =
        """|GET
           |/
           |Action=GetSessionToken&DurationSeconds=3600&Version=2011-06-15
           |host:sts.amazonaws.com
           |x-amz-date:20120519T004356Z
           |
           |host;x-amz-date
           |e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855""".stripMargin

      val expectedStringToSign =
        """|AWS4-HMAC-SHA256
           |20120519T004356Z
           |20120519/us-east-1/sts/aws4_request
           |ec19857897328f82cfb526a6bae44824ad717e58272c3a018545b658ceba425d""".stripMargin

      val signer = {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        cal.set(2012, 4, 19, 0, 43, 56)
        newSigner("sts", cal.getTime)
      }

      WsTestClient.withClient { client =>
        val request =
          client
            .url("https://sts.amazonaws.com")
            .addQueryStringParameters(
              "Action" -> "GetSessionToken",
              "DurationSeconds" -> "3600",
              "Version" -> "2011-06-15")

        val signedRequest = signer.sign(request, "GET", Array.empty)

        test(signer, "cannonicalRequest", expectedCannonicalRequest)
        test(signer, "stringToSign", expectedStringToSign)

        "signed request date header" in {
          signedRequest.headers("X-Amz-Date") must_== Seq("20120519T004356Z")
        }
      }
    }

    "example1 with temp credentials" >> {

      val expectedCannonicalRequest =
        """|GET
           |/
           |Action=GetSessionToken&DurationSeconds=3600&Version=2011-06-15
           |host:sts.amazonaws.com
           |x-amz-date:20120519T004356Z
           |x-amz-security-token:securitytoken
           |
           |host;x-amz-date;x-amz-security-token
           |e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855""".stripMargin

      val expectedStringToSign =
        """|AWS4-HMAC-SHA256
           |20120519T004356Z
           |20120519/us-east-1/sts/aws4_request
           |c3e0a99e512739a75104ce81eb353977af1f5bd8cfd4ade2db6b65d3c6f35d74""".stripMargin

      val tempCredentials = AwsCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", Some("securitytoken"))
      val signer = {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        cal.set(2012, 4, 19, 0, 43, 56)
        newSigner("sts", cal.getTime, tempCredentials)
      }

      WsTestClient.withClient { client =>
        val request =
          client
            .url("https://sts.amazonaws.com")
            .addQueryStringParameters(
              "Action" -> "GetSessionToken",
              "DurationSeconds" -> "3600",
              "Version" -> "2011-06-15")

        val signedRequest = signer.sign(request, "GET", Array.empty)

        test(signer, "cannonicalRequest", expectedCannonicalRequest)
        test(signer, "stringToSign", expectedStringToSign)

        "signed request date header" in {
          signedRequest.headers("X-Amz-Date") must_== Seq("20120519T004356Z")
        }
      }
    }

    "example2 GET Object" >> {

      val expectedCannonicalRequest =
        """|GET
           |/test.txt
           |
           |host:examplebucket.s3.amazonaws.com
           |range:bytes=0-9
           |x-amz-content-sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
           |x-amz-date:20130524T000000Z
           |
           |host;range;x-amz-content-sha256;x-amz-date
           |e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855""".stripMargin

      val expectedStringToSign =
        """|AWS4-HMAC-SHA256
           |20130524T000000Z
           |20130524/us-east-1/s3/aws4_request
           |7344ae5b7ee6c3e7e6b0fe0640412a37625d1fbfff95c48bbb2dc43964946972""".stripMargin

      val expectedSignature = "f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41"

      val expectedAuthorizationHeader = "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,SignedHeaders=host;range;x-amz-content-sha256;x-amz-date,Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41"

      val signer = newSigner()

      WsTestClient.withClient { client =>
        val request =
          client
            .url("http://examplebucket.s3.amazonaws.com/test.txt")
            .addHttpHeaders(
              signer.amzContentSha256(Array.empty),
              "Range" -> "bytes=0-9")

        val signedRequest = signer.sign(request, "GET", Array.empty)

        test(signer, "cannonicalRequest", expectedCannonicalRequest)
        test(signer, "stringToSign", expectedStringToSign)
        test(signer, "signature", expectedSignature)
        test(signer, "authorizationHeader", expectedAuthorizationHeader)

        "signed request" in {
          signedRequest.headers("Authorization") must_== Seq(expectedAuthorizationHeader)
        }
      }
    }

    "example3 PUT Object" >> {

      val expectedCannonicalRequest =
        if (play.core.PlayVersion.current.startsWith("2.7."))
          """|PUT
             |/test%24file.text
             |
             |content-type:text/plain; charset=UTF-8
             |date:Fri, 24 May 2013 00:00:00 GMT
             |host:examplebucket.s3.amazonaws.com
             |x-amz-content-sha256:44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072
             |x-amz-date:20130524T000000Z
             |x-amz-storage-class:REDUCED_REDUNDANCY
             |
             |content-type;date;host;x-amz-content-sha256;x-amz-date;x-amz-storage-class
             |44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072""".stripMargin
        else
          """|PUT
             |/test%24file.text
             |
             |content-type:text/plain
             |date:Fri, 24 May 2013 00:00:00 GMT
             |host:examplebucket.s3.amazonaws.com
             |x-amz-content-sha256:44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072
             |x-amz-date:20130524T000000Z
             |x-amz-storage-class:REDUCED_REDUNDANCY
             |
             |content-type;date;host;x-amz-content-sha256;x-amz-date;x-amz-storage-class
             |44ce7dd67c959e0d3524ffac1771dfbba87d2b6b4b4e99e42034a8b803f8b072""".stripMargin

      val expectedStringToSign =
        if (play.core.PlayVersion.current.startsWith("2.7."))
          """|AWS4-HMAC-SHA256
             |20130524T000000Z
             |20130524/us-east-1/s3/aws4_request
             |f8bc133040708ec4d9462312b10d102a942e6984358391d90e7df4e66d07b0e0""".stripMargin
        else
          """|AWS4-HMAC-SHA256
             |20130524T000000Z
             |20130524/us-east-1/s3/aws4_request
             |0f10f84734b64a0f7ac71f28a26c6d34d07bc39df988e9e9c39bfc1fc154b6cd""".stripMargin

      val expectedSignature =
        if (play.core.PlayVersion.current.startsWith("2.7."))
          "fae025cee8702959355df87bdd1215eb556e1d70417f5ce18edd46e1dab34d40"
        else
          "f093977030bf8d8069918f1b3546fd02cf697d43e763c40d58c109a2e197bdac"

      val expectedAuthorizationHeader =
        if (play.core.PlayVersion.current.startsWith("2.7."))
        "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,SignedHeaders=content-type;date;host;x-amz-content-sha256;x-amz-date;x-amz-storage-class,Signature=fae025cee8702959355df87bdd1215eb556e1d70417f5ce18edd46e1dab34d40"
        else
        "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,SignedHeaders=content-type;date;host;x-amz-content-sha256;x-amz-date;x-amz-storage-class,Signature=f093977030bf8d8069918f1b3546fd02cf697d43e763c40d58c109a2e197bdac"

      val signer = newSigner()
      val body = "Welcome to Amazon S3."

      implicit def bodyWritable: BodyWritable[String] = BodyWritable(s => InMemoryBody(ByteString(s)), "text/plain")

      WsTestClient.withClient { client =>
        val request =
          client
            .url("http://examplebucket.s3.amazonaws.com/" + URLEncoder.encode("test$file.text", "UTF-8"))
            .addHttpHeaders(
              "x-amz-storage-class" -> "REDUCED_REDUNDANCY",
              "Date" -> "Fri, 24 May 2013 00:00:00 GMT",
              signer.amzContentSha256(body.getBytes))
            .withBody(body)

        val signedRequest = signer.sign(request, "PUT", body.getBytes)

        test(signer, "cannonicalRequest", expectedCannonicalRequest)
        test(signer, "stringToSign", expectedStringToSign)
        test(signer, "signature", expectedSignature)
        test(signer, "authorizationHeader", expectedAuthorizationHeader)

        "signed request" in {
          signedRequest.headers("Authorization") must_== Seq(expectedAuthorizationHeader)
        }
      }
    }

    "example4 GET Bucket Lifecycle" >> {

      val expectedCannonicalRequest =
        """|GET
           |/
           |lifecycle=
           |host:examplebucket.s3.amazonaws.com
           |x-amz-content-sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
           |x-amz-date:20130524T000000Z
           |
           |host;x-amz-content-sha256;x-amz-date
           |e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855""".stripMargin

      val expectedStringToSign =
        """|AWS4-HMAC-SHA256
           |20130524T000000Z
           |20130524/us-east-1/s3/aws4_request
           |9766c798316ff2757b517bc739a67f6213b4ab36dd5da2f94eaebf79c77395ca""".stripMargin

      val expectedSignature =
        "fea454ca298b7da1c68078a5d1bdbfbbe0d65c699e0f91ac7a200a0136783543"

      val expectedAuthorizationHeader =
        "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,SignedHeaders=host;x-amz-content-sha256;x-amz-date,Signature=fea454ca298b7da1c68078a5d1bdbfbbe0d65c699e0f91ac7a200a0136783543"

      val signer = newSigner()

      WsTestClient.withClient { client =>
        val request =
          client
            .url("http://examplebucket.s3.amazonaws.com")
            .addQueryStringParameters("lifecycle" -> "")
            .addHttpHeaders(signer.amzContentSha256(Array.empty))

        val signedRequest = signer.sign(request, "GET", Array.empty)

        test(signer, "cannonicalRequest", expectedCannonicalRequest)
        test(signer, "stringToSign", expectedStringToSign)
        test(signer, "signature", expectedSignature)
        test(signer, "authorizationHeader", expectedAuthorizationHeader)

        "signed request" in {
          signedRequest.headers("Authorization") must_== Seq(expectedAuthorizationHeader)
        }
      }
    }

    "example5 Get Bucket (List Objects)" >> {

      val expectedCannonicalRequest =
        """|GET
           |/
           |max-keys=2&prefix=J
           |host:examplebucket.s3.amazonaws.com
           |x-amz-content-sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
           |x-amz-date:20130524T000000Z
           |
           |host;x-amz-content-sha256;x-amz-date
           |e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855""".stripMargin

      val expectedStringToSign =
        """|AWS4-HMAC-SHA256
           |20130524T000000Z
           |20130524/us-east-1/s3/aws4_request
           |df57d21db20da04d7fa30298dd4488ba3a2b47ca3a489c74750e0f1e7df1b9b7""".stripMargin

      val expectedSignature =
        "34b48302e7b5fa45bde8084f4b7868a86f0a534bc59db6670ed5711ef69dc6f7"

      val expectedAuthorizationHeader =
        "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,SignedHeaders=host;x-amz-content-sha256;x-amz-date,Signature=34b48302e7b5fa45bde8084f4b7868a86f0a534bc59db6670ed5711ef69dc6f7"

      val signer = new AmzContentHeaderSignerSpy()

      WsTestClient.withClient { client =>
        val request =
          client
            .url("http://examplebucket.s3.amazonaws.com")
            .addQueryStringParameters(
              "prefix" -> "J",
              "max-keys" -> "2")

        val signedRequest = signer.sign(request, "GET", Array.empty)

        test(signer, "cannonicalRequest", expectedCannonicalRequest)
        test(signer, "stringToSign", expectedStringToSign)
        test(signer, "signature", expectedSignature)
        test(signer, "authorizationHeader", expectedAuthorizationHeader)

        "don't include headers twice" in {
          signedRequest.headers("X-Amz-Content-Sha256").size must_== 1
        }

        "signed request" in {
          signedRequest.headers("Authorization") must_== Seq(expectedAuthorizationHeader)
        }
      }
    }

    "example6 url signature" in {

      val expectedCannonicalRequest =
        """|GET
           |/test.txt
           |X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20130524T000000Z&X-Amz-Expires=86400&X-Amz-SignedHeaders=host
           |host:examplebucket.s3.amazonaws.com
           |
           |host
           |UNSIGNED-PAYLOAD""".stripMargin

      val expectedStringToSign =
        """|AWS4-HMAC-SHA256
           |20130524T000000Z
           |20130524/us-east-1/s3/aws4_request
           |3bfa292879f6447bbcda7001decf97f4a54dc650c8942174ae0a9121cf58ad04""".stripMargin

      val expectedSignature =
        "aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404"

      val expectedUrl = "https://examplebucket.s3.amazonaws.com/test.txt?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20130524T000000Z&X-Amz-Expires=86400&X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404&X-Amz-SignedHeaders=host"

      val signer = newSigner()
      val url = signer.signUrl("GET", "https://examplebucket.s3.amazonaws.com/test.txt", 86400)

      test(signer, "cannonicalRequest", expectedCannonicalRequest)
      test(signer, "stringToSign", expectedStringToSign)
      test(signer, "signature", expectedSignature)

      case class Content(url: String, queryString: Set[String])

      class Url(url: String) {
        val content = {
          val Array(urlPart, queryStringPart) = url.split("\\?")
          Content(urlPart, queryStringPart.split("&").toSet)
        }
      }

      "signed url" in {
        new Url(url).content must_== new Url(expectedUrl).content
      }
    }
  }

  val defaultCredentials = AwsCredentials("AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
  val defaultDate = {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
    cal.set(2013, 4, 24, 0, 0, 0)
    cal.getTime
  }

  def newSigner(service: String = "s3", date: Date = defaultDate, credentials: AwsCredentials = defaultCredentials) =
    new SignerSpy(service, date, credentials)

  def test(signer: { def results: mutable.Map[String, String] }, name: String, expected: String) =
    name in {
      signer.results(name) must_== expected
    }

  class SignerSpy(service: String = "s3", date: Date = defaultDate, credentials: AwsCredentials = defaultCredentials)
    extends Aws4Signer(credentials, service, "us-east-1") {
    val results = mutable.Map.empty[String, String]

    override def currentDate = date

    override def createAuthorizationHeader(scope: Scope, signedHeaders: String, signature: String): String = {
      val authorizationHeader = super.createAuthorizationHeader(scope, signedHeaders, signature)
      results += "authorizationHeader" -> authorizationHeader
      authorizationHeader
    }

    override def createStringToSign(scope: Scope, cannonicalRequest: String): String = {
      val stringToSign = super.createStringToSign(scope, cannonicalRequest)
      results += "stringToSign" -> stringToSign
      stringToSign
    }

    override def createCannonicalRequest(request: AwsRequest) = {
      val cannonicalRequest = super.createCannonicalRequest(request)
      results += "cannonicalRequest" -> cannonicalRequest
      cannonicalRequest
    }

    override def createSignature(stringToSign: String, scope: Scope) = {
      val signature = super.createSignature(stringToSign, scope)
      results += "signature" -> signature
      signature
    }
  }

  class AmzContentHeaderSignerSpy extends SignerSpy {
    override def sign(request: WSRequest, method: String, body: Array[Byte]): WSRequest =
      super.sign(request.addHttpHeaders(amzContentSha256(Array.empty)), method, body)
  }
}
