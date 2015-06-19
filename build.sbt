name := "play-s3"

organization := "net.kaliber"

scalaVersion := "2.11.6"

crossScalaVersions := Seq("2.10.4", "2.11.6")

releaseCrossBuild := true

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws" % "2.4.0",
  "com.typesafe.play" %% "play-test" % "2.4.0" % "test",
  "org.specs2" %% "specs2-core" % "3.6.1" % "test"
)

publishTo := {
  val repo = if (version.value endsWith "SNAPSHOT") "snapshot" else "release"
  Some("Kaliber Internal " + repo.capitalize + " Repository" at "https://jars.kaliber.io/artifactory/libs-" + repo + "-local")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

resolvers ++= Seq(
  "Typesafe Release Repository" at "http://repo.typesafe.com/typesafe/releases",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)

scalacOptions ++= Seq("-feature", "-deprecation")


// https://github.com/playframework/playframework/issues/4827

fork in Test := true

javaOptions in Test ++= Seq(
  "-Dconfig.file=test/conf/application.conf"
)


