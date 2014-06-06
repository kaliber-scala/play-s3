name := "play-s3"

version := "4.0.1"

organization := "nl.rhinofly"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % "2.2.0",
  "nl.rhinofly" %% "play-aws-utils" % "3.0.0",
  "com.typesafe.play" %% "play-test" % "2.2.3" % "test",
  "org.specs2" %% "specs2" % "2.3.12" % "test"
)

publishTo := {
    val repo = if (version.value endsWith "SNAPSHOT") "snapshot" else "release"
    Some("Rhinofly Internal " + repo.capitalize + " Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-" + repo + "-local")
  }

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")  
  
resolvers ++= Seq(
  "Rhinofly Internal Release Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-release-local",
  "Typesafe Release Repository" at "http://repo.typesafe.com/typesafe/releases")
  
def rhinoflyRepo(version: String) = {
    val repo = if (version endsWith "SNAPSHOT") "snapshot" else "release"
    Some("Rhinofly Internal " + repo.capitalize + " Repository" at "http://maven-repository.rhinofly.net:8081/artifactory/libs-" + repo + "-local")
  }

//fork in Test := true

scalacOptions ++= Seq("-feature", "-deprecation")
