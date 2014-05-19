import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName = "play-s3"
  val appVersion = "4.0.0"

  val appDependencies = Seq(
    "nl.rhinofly" %% "play-aws-utils" % "3.0.0")

  def rhinoflyRepo(version: String) = {
    val repo = if (version endsWith "SNAPSHOT") "snapshot" else "release"
    Some("Rhinofly Internal " + repo.capitalize + " Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-" + repo + "-local")
  }

  val main = play.Project(appName, appVersion, appDependencies).settings(
    organization := "nl.rhinofly",
    resolvers += rhinoflyRepo("RELEASE").get,
    publishTo <<= version(rhinoflyRepo),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    scalacOptions += "-feature")

}
