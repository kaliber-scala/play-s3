import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

  val appName = "api-s3"
  val appVersion = "1.0"

  val appDependencies = Seq(
    "nl.rhinofly" %% "api-aws-utils" % "1.1")

  val rhinoflyRepo = "Rhinofly Internal Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local"

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    organization := "nl.rhinofly",
    resolvers += rhinoflyRepo,
    publishTo := Some(rhinoflyRepo),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"))

}
