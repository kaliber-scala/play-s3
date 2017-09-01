package fly.play.s3.upload

import java.util.Calendar

import fly.play.aws.AwsDates.iso8601DateFormat
import fly.play.aws.SimpleAwsCredentials
import fly.play.aws.policy.Condition.{acl, contentLengthRange, fromTuple, header, key, meta, string, successActionRedirect, xAmzSecurityToken}
import fly.play.aws.policy.{Eq, PolicyBuilder, StartsWith}
import fly.play.s3.{PUBLIC_READ, S3Signer}
import org.specs2.mutable.Specification
import play.api.http.HeaderNames.{CACHE_CONTROL, CONTENT_DISPOSITION, CONTENT_TYPE}
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper

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

  def bigPolicy = PolicyBuilder(date)(signer)
    .withConditions(
      "bucket" -> "johnsmith",
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
