package fly.play.aws

import java.security.MessageDigest
import java.util.Date

import scala.language.implicitConversions

import org.apache.commons.codec.binary.Base64

import fly.play.aws.policy.AwsPolicy
import fly.play.aws.policy.Condition
import fly.play.aws.policy.PolicyBuilder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import play.api.libs.ws.WSRequestHolder

class Aws4Signer(
  val credentials: AwsCredentials,
  val service: String,
  val region: String) extends AwsSigner {

  val DEFAULT_ENCODING = "UTF-8"
  private val algorithm = "AWS4-HMAC-SHA256"
  protected def currentDate = new Date

  private val AwsCredentials(accessKeyId, secretKey, token) = credentials

  def sign(request: WSRequestHolder, method: String, body: Array[Byte]): WSRequestHolder =
    addAuthorizationHeaders(request, method, body)

  def createPolicy(policyBuilder: PolicyBuilder): AwsPolicy = {

    val scope = Scope(currentDate)

    val policyWithSecurityConditions = addSecurityConditions(policyBuilder, scope)

    val encoded = asBase64(policyWithSecurityConditions)
    val signature = createSignature(encoded, scope)
    val (signatureName, Seq(signatureValue)) = amzSignature(signature)

    AwsPolicy(
      encoded,
      signatureName,
      signatureValue,
      policyWithSecurityConditions.conditions
    )
  }

  def signUrl(method: String, url: String, expiresIn: Int, queryString: Map[String, Seq[String]] = Map.empty): String = {
    require(expiresIn >= 1, "expiresIn must at least be 1 second")
    require(expiresIn <= 604800, "expiresIn can be no longer than 7 days (604800 seconds)")

    val scope = Scope(currentDate)

    val headersToSign = ("host" +: amzSecurityToken.map(_ => AMZ_SECURITY_TOKEN).toSeq).mkString(",")

    val queryStringWithRequiredParams = queryString ++
      Map(
        amzAlgorithm,
        amzCredential(scope),
        amzExpires(expiresIn),
        amzSignedHeaders(headersToSign),
        amzDate(scope))

    val request = AwsRequest(method, url, Map.empty, queryStringWithRequiredParams, None)

    val signature = createRequestSignature(scope, request)

    val signedQueryString = queryStringWithRequiredParams + amzSignature(signature)

    url + "?" + queryStringAsString(signedQueryString)
  }

  case class Scope(date: Date) {
    private val dateStamp = AwsDates.dateStampFormat format date

    val TERMINATOR = "aws4_request"

    lazy val value = dateStamp + "/" + region + "/" + service + "/" + TERMINATOR

    lazy val key = {
      var key = sign(dateStamp, "AWS4" + secretKey)
      key = sign(region, key)
      key = sign(service, key)
      key = sign(TERMINATOR, key)
      key
    }

    lazy val dateTime = AwsDates.dateTimeFormat format date

    lazy val credentials = accessKeyId + "/" + value
  }

  private def addAuthorizationHeaders(wsRequest: WSRequestHolder, method: String, body: Array[Byte]): WSRequestHolder = {
    val request = AwsRequest(
      method,
      wsRequest.url,
      wsRequest.headers,
      wsRequest.queryString,
      Some(body))

    val extraHeaders = createAuthorizationHeaders(request)

    val simplifiedHeaders = extraHeaders.toSeq.flatMap {
      case (name, values) => values.map(name -> _)
    }
    wsRequest.withHeaders(simplifiedHeaders: _*)
  }

  private def createAuthorizationHeaders(request: AwsRequest): Map[String, Seq[String]] = {

    val scope = Scope(currentDate)

    val extraHeaders = (amzDate(scope) +: amzSecurityToken.toSeq).toMap

    val requestWithExtraHeaders = request.copy(headers = request.headers ++ extraHeaders)

    val signature = createRequestSignature(scope, requestWithExtraHeaders)

    val authorizationHeaderValue = createAuthorizationHeader(scope, requestWithExtraHeaders.signedHeaders, signature)

    val authorizationHeader = "Authorization" -> Seq(authorizationHeaderValue)

    extraHeaders + authorizationHeader
  }

  protected def createRequestSignature(scope: Scope, request: AwsRequest) = {

    val cannonicalRequest = createCannonicalRequest(request)

    val stringToSign = createStringToSign(scope, cannonicalRequest)

    val signature = createSignature(stringToSign, scope)

    signature
  }

  protected def createCannonicalRequest(request: AwsRequest) = {

    val AwsRequest(method, _, headers, queryString, body) = request

    val normalizedHeaders = request.normalizedHeaders

    val payloadHash =
      normalizedHeaders
        .get(CONTENT_SHA_HEADER_NAME.toLowerCase)
        .flatMap(_.headOption)
        .orElse(body map hexHash)
        .getOrElse("UNSIGNED-PAYLOAD")

    val resourcePath =
      request.uri.getRawPath match {
        case "" | null => "/"
        case path      => path
      }

    val cannonicalRequest =
      method + "\n" +
        /* resourcePath */
        resourcePath + "\n" +
        /* queryString */
        queryString
        .map { case (k, v) => k -> v.headOption.getOrElse("") }
        .toSeq.sorted
        .map { case (k, v) => AwsUrlEncoder.encode(k) + "=" + AwsUrlEncoder.encode(v) }
        .mkString("&") + "\n" +
        /* headers */
        normalizedHeaders.map { case (k, v) => k + ":" + v.mkString(",") + "\n" }.mkString + "\n" +
        /* signed headers */
        request.signedHeaders + "\n" +
        /* payload */
        payloadHash

    cannonicalRequest
  }

  protected def createStringToSign(scope: Scope, cannonicalRequest: String): String =
    algorithm + "\n" +
      scope.dateTime + "\n" +
      scope.value + "\n" +
      toHex(hash(cannonicalRequest))

  protected def createSignature(stringToSign: String, scope: Scope) =
    toHex(sign(stringToSign, scope.key))

  protected def createAuthorizationHeader(scope: Scope, signedHeaders: String, signature: String): String =
    algorithm + " " +
      "Credential=" + scope.credentials + "," +
      "SignedHeaders=" + signedHeaders + "," +
      "Signature=" + signature

  private def queryStringAsString(queryString: Map[String, Seq[String]]) =
    queryString.map {
      case (k, v) => AwsUrlEncoder.encode(k) + "=" + v.map(AwsUrlEncoder.encode).mkString(",")
    }.mkString("&")

  private def hexHash(payload: Array[Byte]) = toHex(hash(payload))

  private def asBase64(policyBuilder: PolicyBuilder) = {
    val policyJsonBytes = policyBuilder.json.toString getBytes DEFAULT_ENCODING
    new String(Base64 encodeBase64 policyJsonBytes)
  }

  private def addSecurityConditions(policyBuilder: PolicyBuilder, scope: Scope) = {

    import Condition.string
    import scala.language.implicitConversions

    implicit def toCondition(p: (String, Seq[String])): Condition =
      p._1 -> p._2.head

    policyBuilder
      .withConditions(
        amzAlgorithm,
        amzCredential(scope),
        amzDate(scope))
  }

  private def toHex(b: Array[Byte]): String = b.map("%02x" format _).mkString

  private def sign(data: Array[Byte], key: Array[Byte]): Array[Byte] = {
    val mac = Mac getInstance "HmacSHA256"
    mac init new SecretKeySpec(key, mac.getAlgorithm)
    mac doFinal data
  }
  private def sign(str: String, key: String): Array[Byte] = sign(str, key getBytes DEFAULT_ENCODING)
  private def sign(str: String, key: Array[Byte]): Array[Byte] = sign(str getBytes DEFAULT_ENCODING, key)
  private def hash(str: String): Array[Byte] = hash(str getBytes DEFAULT_ENCODING)

  private def hash(bytes: Array[Byte]): Array[Byte] = {
    val md = MessageDigest getInstance "SHA-256"
    md update bytes
    md.digest
  }

  private val CONTENT_SHA_HEADER_NAME = "X-Amz-Content-Sha256"
  private val AMZ_SECURITY_TOKEN = "X-Amz-Security-Token"

  val amzAlgorithm = "X-Amz-Algorithm" -> Seq(algorithm)

  def amzDate(scope: Scope) = "X-Amz-Date" -> Seq(scope.dateTime)

  def amzCredential(scope: Scope) = "X-Amz-Credential" -> Seq(scope.credentials)

  def amzSecurityToken = token.map(t => AMZ_SECURITY_TOKEN -> Seq(t))

  def amzSignature(signature: String) = "X-Amz-Signature" -> Seq(signature)

  def amzExpires(expiresIn: Int) = "X-Amz-Expires" -> Seq(expiresIn.toString)

  def amzSignedHeaders(headers: String) = "X-Amz-SignedHeaders" -> Seq(headers)

  def amzContentSha256(content: Array[Byte]) = CONTENT_SHA_HEADER_NAME -> hexHash(content)
}