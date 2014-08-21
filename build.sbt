name := "play-s3"

version := "5.0.1-SNAPSHOT"

organization := "nl.rhinofly"

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % "2.3.0",
  "nl.rhinofly" %% "play-aws-utils" % "4.0.3-SNAPSHOT",
  "com.typesafe.play" %% "play-test" % "2.3.0" % "test",
  "org.specs2" %% "specs2" % "2.3.12" % "test"
)

publishTo := {
    Some("WiredThing Internal Forks Repository" at "http://artifactory.wiredthing.com/artifactory/libs-forked-local")
  }

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")  
  
resolvers ++= Seq(
  "WiredThing Internal Forks Repository" at "http://artifactory.wiredthing.com/artifactory/libs-forked-local",
  "Rhinofly Internal Release Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local",
  "Typesafe Release Repository" at "http://repo.typesafe.com/typesafe/releases")
  
def rhinoflyRepo(version: String) = {
    val repo = if (version endsWith "SNAPSHOT") "snapshot" else "release"
    Some("WiredThing Internal " + repo.capitalize + " Repository" at "http://artifactory.wiredthing.com/artifactory/libs-forked-local")
  }

//fork in Test := true

scalacOptions ++= Seq("-feature", "-deprecation")
