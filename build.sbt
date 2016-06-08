name := "play-s3"

organization := "net.kaliber"

licenses += ("MIT", url("https://spdx.org/licenses/MIT"))
homepage := Some(url("https://github.com/Kaliber/play-s3"))

scalaVersion := "2.11.6"
crossScalaVersions := Seq("2.11.6")
releaseCrossBuild := true

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws"     % "2.5.0" % "provided",
  "com.typesafe.play" %% "play-test"   % "2.5.0" % "test",
  "org.specs2"        %% "specs2-core" % "3.6.1" % "test"
)

bintrayOrganization := Some("kaliber")
bintrayReleaseOnPublish in ThisBuild := false

resolvers ++= Seq(
  "Typesafe Release Repository" at "http://repo.typesafe.com/typesafe/releases",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)

scalacOptions ++= Seq("-feature", "-deprecation")

// https://github.com/playframework/playframework/issues/4827

fork in Test := true
javaOptions in Test += "-Dconfig.file=test/conf/application.conf"
publishArtifact in Test := false

publishMavenStyle := true
pomIncludeRepository := { _ => false }

pomExtra := (
  <scm>
    <connection>scm:git:github.com/Kaliber/play-s3.git</connection>
    <developerConnection>scm:git:git@github.com:Kaliber/play-s3.git</developerConnection>
    <url>https://github.com/Kaliber/play-s3</url>
  </scm>
  <developers>
    <developer>
      <id>Kaliber</id>
      <name>Kaliber Interactive</name>
      <url>https://kaliber.net/</url>
    </developer>
  </developers>
)
