package fly.play.s3.upload

import java.util.Date
import play.api.libs.json.Writes
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import fly.play.s3.ACL
import fly.play.s3.PUBLIC_READ
import play.api.http.HeaderNames
import fly.play.aws.Aws.dates.iso8601DateFormat
import scala.language.implicitConversions
import fly.play.aws.auth.SignerUtils
import fly.play.s3.S3Signer

case class PolicyBuilder(expiration: Date, conditions: Seq[Condition] = Seq.empty)(implicit val signer: S3Signer) {

  def withConditions(conditions: Condition*) =
    this.copy(conditions = this.conditions ++ conditions)

  lazy val json =
    Json.obj(
      "expiration" -> iso8601DateFormat.format(expiration),
      "conditions" -> conditions)

  lazy val encoded =
    signer.base64Encode(json.toString.getBytes(signer.DEFAULT_ENCODING))

  lazy val signature =
    signer.createSignature(encoded)

}

object PolicyBuilder {

  def apply(bucketName: String, expiration: Date)(implicit signer: S3Signer): PolicyBuilder =
    PolicyBuilder(expiration).withConditions(Condition.string("bucket") eq bucketName)
}

trait Condition {
  def json: JsValue
}

trait ElementValueBuilder[T] {
  val element: String
  def toString(value: T): String
}

trait EqBuilder[T] extends ElementValueBuilder[T] {
  def eq(value: T) = Eq(element, toString(value))
}

trait StartsWithBuilder[T] extends ElementValueBuilder[T] {

  def startsWith(value: T) = StartsWith(element, toString(value))
  def any = StartsWith(element, "")
}

trait FromRangeBuilder {
  def from(from: Long): ToRangeBuilder
}

trait ToRangeBuilder {
  def to(to: Long): Range
}

trait StringCondition extends Condition {
  val element: String
  val value: String

  lazy val escapedElement = escape(element)
  lazy val escapedValue = escape(value)

  def escape(value: String): String =
    value.replaceAllLiterally("$", """\$""")
}

object Condition {

  implicit val writes: Writes[Condition] = Writes[Condition](_.json)

  implicit def fromTuple(t: (String, String)): Eq = Eq(t._1, t._2)

  val acl =
    new EqBuilder[ACL] with StartsWithBuilder[ACL] {
      val element = "acl"
      def toString(acl: ACL) = acl.value
    }

  val contentLengthRange =
    new FromRangeBuilder {
      def from(from: Long) =
        new ToRangeBuilder {
          def to(to: Long) = Range("content-length-range", from, to)
        }
    }

  val key = string("key")
  
  val successActionRedirect = string("success_action_redirect")

  val successActionStatus =
    new EqBuilder[Int] {
      val element = "success_action_status"
      def toString(value: Int) = value.toString
    }

  def xAmzSecurityToken(userToken: String, productToken: String) =
    Eq("x-amz-security-token", userToken + "," + productToken)

  val header = string _

  def meta(name: String) = string("x-amz-meta-" + name)
  
  def string(name: String): EqBuilder[String] with StartsWithBuilder[String] =
    new EqBuilder[String] with StartsWithBuilder[String] {
      val element = name
      def toString(value: String) = value
    }
}

case class Eq(element: String, value: String) extends StringCondition {
  lazy val json = Json.obj(escapedElement -> escapedValue)
}

case class StartsWith(element: String, value: String) extends StringCondition {
  lazy val json = Json.arr("starts-with", "$" + escapedElement, escapedValue)
}

case class Range(element: String, from: Long, to: Long) extends Condition {
  lazy val json = Json.arr(element, from, to)
}
