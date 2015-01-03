package fly.play.aws

import java.net.URI
import scala.collection.SortedMap
import scala.Ordering

case class AwsRequest(
  method: String,
  url: String,
  headers: Map[String, Seq[String]],
  queryString: Map[String, Seq[String]],
  body: Option[Array[Byte]]) {

  lazy val uri = URI.create(url)

  lazy val normalizedHeaders: SortedMap[String, Seq[String]] = {

    val allHeaders = headers + ("host" -> Seq(uri.getHost))

    val lowerCaseKeyHeaders =
      allHeaders.map { case (k, v) => k.toLowerCase -> v }

    val sortedLowerCaseHeaders =
      SortedMap.empty(Ordering[String]) ++ lowerCaseKeyHeaders

    sortedLowerCaseHeaders
  }

  lazy val signedHeaders: String = normalizedHeaders.keys.mkString(";")
}