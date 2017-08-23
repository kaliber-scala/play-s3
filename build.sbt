name := "play-s3"

organization := "net.kaliber"

scalaVersion := "2.12.2"

crossScalaVersions := Seq("2.12.2", "2.11.6")

releaseCrossBuild := true

val playVersion = "2.6.3"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws"     % playVersion % "provided",
  "com.typesafe.play" %% "play-test"   % playVersion % "test",
  "com.typesafe.play" %% "play-specs2" % playVersion % "test",
  "com.typesafe.play" %% "play-ahc-ws" % playVersion % "test",
  "com.typesafe.play" %% "play-logback" % playVersion % "test"
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

javaOptions in Test += "-Dconfig.file=test/conf/application.conf"
