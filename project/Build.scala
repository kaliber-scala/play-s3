import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName = "play-s3"
  val appVersion = "3.3.4"

  val appDependencies = Seq(
    "nl.rhinofly" %% "play-aws-utils" % "2.4.2")

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
