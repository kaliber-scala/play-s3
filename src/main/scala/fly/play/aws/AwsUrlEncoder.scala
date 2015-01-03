package fly.play.aws

object AwsUrlEncoder {
  def encodePath(value: String) =
    encode(value).replace("%2F", "/")

  def encode(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8")
      .replace("+", "%20")
      .replace("*", "%2A")
      .replace("%7E", "~")
}