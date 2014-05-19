package fly.play.s3.upload

import java.util.Calendar
import org.specs2.mutable.Specification
import fly.play.aws.Aws.dates.iso8601DateFormat
import fly.play.aws.auth.SimpleAwsCredentials
import fly.play.s3.PUBLIC_READ
import fly.play.s3.S3Signer
import fly.play.s3.upload.Condition.acl
import fly.play.s3.upload.Condition.contentLengthRange
import fly.play.s3.upload.Condition.fromTuple
import fly.play.s3.upload.Condition.header
import fly.play.s3.upload.Condition.key
import fly.play.s3.upload.Condition.meta
import fly.play.s3.upload.Condition.string
import fly.play.s3.upload.Condition.successActionRedirect
import fly.play.s3.upload.Condition.xAmzSecurityToken
import play.api.http.HeaderNames.CACHE_CONTROL
import play.api.http.HeaderNames.CONTENT_DISPOSITION
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import fly.play.aws.auth.Aws4Signer
import fly.play.aws.auth.SignerUtils
import fly.play.aws.auth.Signer
import play.api.libs.ws.WS
import play.api.http.Writeable
import fly.play.aws.auth.AwsCredentials
import fly.play.aws.auth.SimpleAwsCredentials

object PolicyBuilderSpec extends Specification {
  "PolicyBuilder" should {

    "create the correct json" in {

      testData.bigPolicy.json ===
        Json.obj(
          "expiration" -> iso8601DateFormat.format(testData.date),
          "conditions" -> Json.arr(
            Json.obj("bucket" -> "johnsmith"),
            Json.obj("acl" -> PUBLIC_READ.value),
            Json.arr("content-length-range", 10, 100),
            Json.arr("starts-with", "$key", "test/"),
            Json.arr("starts-with", "$success_action_redirect", ""),
            Json.obj(CACHE_CONTROL -> "no-cache"),
            Json.arr("starts-with", "$" + CONTENT_TYPE, "text/"),
            Json.obj(CONTENT_DISPOSITION -> "attachment;fileName=\"test.text\""),
            Json.obj("x-amz-meta-test1" -> "abc"),
            Json.obj("x-amz-meta-test2" -> "def"),
            Json.arr("starts-with", "$x-amz-meta-test3", "ghi"),
            Json.obj("x-amz-security-token" -> "x,y")))
    }

    "escape the json correctly" in {

      Eq("el$ement", "val$ue").json ===
        Json.obj("""el\$ement""" -> """val\$ue""")

      StartsWith("el$ement", "val$ue").json ===
        Json.arr("starts-with", """$el\$ement""", """val\$ue""")
    }
  }
}

object testData {
  def signer = new S3Signer(SimpleAwsCredentials("fake", "fake"), "fake")

  def createDate(year: Int, month: Int, day: Int) = {
    val calendar = Calendar.getInstance
    calendar.set(year, month, day, 12, 0)
    calendar.getTime
  }

  val date = createDate(2007, 12, 1)

  def bigPolicy = PolicyBuilder("johnsmith", date)(signer)
    .withConditions(
      acl eq PUBLIC_READ,
      contentLengthRange from 10 to 100,
      key startsWith "test/",
      successActionRedirect.any,
      CACHE_CONTROL -> "no-cache",
      header(CONTENT_TYPE) startsWith "text/",
      string(CONTENT_DISPOSITION) eq "attachment;fileName=\"test.text\"",
      "x-amz-meta-test1" -> "abc",
      string("x-amz-meta-test2") eq "def",
      meta("test3") startsWith "ghi",
      xAmzSecurityToken("x", "y"))
}
