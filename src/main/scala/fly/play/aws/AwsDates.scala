package fly.play.aws

import java.util.SimpleTimeZone
import java.text.SimpleDateFormat
import java.util.Locale

object AwsDates {
  lazy val timeZone = new SimpleTimeZone(0, "UTC")

  def dateFormat(format: String, locale: Locale = Locale.getDefault): SimpleDateFormat = {
    val df = new SimpleDateFormat(format, locale)
    df setTimeZone timeZone
    df
  }

  lazy val dateTimeFormat = dateFormat("yyyyMMdd'T'HHmmss'Z'")
  lazy val dateStampFormat = dateFormat("yyyyMMdd")

  lazy val iso8601DateFormat = dateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  lazy val rfc822DateFormat = dateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
}