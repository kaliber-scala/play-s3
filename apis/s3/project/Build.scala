import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "api-s3"
    val appVersion      = "1.0"

    val appDependencies = Seq(
      "nl.rhinofly" %% "api-utils" % "1.0"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
       organization := "nl.rhinofly"
    )

}
