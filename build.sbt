val playVersion = "2.7.0"
lazy val root = (project in file("."))
  .settings(
    name := "play-s3",
    organization := "net.kaliber",
    scalaVersion := "2.12.2",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ws"     % playVersion % "provided",
      "com.typesafe.play" %% "play-test"   % playVersion % "test",
      "com.typesafe.play" %% "play-specs2" % playVersion % "test",
      "com.typesafe.play" %% "play-ahc-ws" % playVersion % "test",
      "com.typesafe.play" %% "play-logback" % playVersion % "test",
      "commons-codec" % "commons-codec" % "1.11" % "provided"
    )
  )
  .settings(bintraySettings: _*)

lazy val bintraySettings = Seq(
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  homepage := Some(url("https://github.com/kaliber-scala/play-s3")),
  bintrayOrganization := Some("kaliber-scala"),
  bintrayReleaseOnPublish := false,
  publishMavenStyle := true,
  crossScalaVersions := Seq("2.12.2", "2.11.6"),
  releaseCrossBuild := true,

  pomExtra := (
    <scm>
      <connection>scm:git@github.com:kaliber-scala/play-s3.git</connection>
      <developerConnection>scm:git@github.com:kaliber-scala/play-s3.git</developerConnection>
      <url>https://github.com/kaliber-scala/play-s3</url>
    </scm>
    <developers>
      <developer>
        <id>Kaliber</id>
        <name>Kaliber Interactive</name>
        <url>https://kaliber.net/</url>
      </developer>
    </developers>
    )
)

scalacOptions ++= Seq("-feature", "-deprecation")

fork in Test := true

javaOptions in Test += "-Dconfig.file=test/conf/application.conf"

// Release
import ReleaseTransformations._
releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  releaseStepTask(bintrayRelease in root),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
