ThisBuild / baseVersion := "0.1"

ThisBuild / organization := "com.armanbilge"
ThisBuild / publishGithubUser := "armanbilge"
ThisBuild / publishFullName := "Arman Bilge"
ThisBuild / startYear := Some(2021)

ThisBuild / homepage := Some(url("https://github.com/armanbilge/gcp4s"))
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/armanbilge/gcp4s"), "git@github.com:armanbilge/gcp4s.git"))
sonatypeCredentialHost := "s01.oss.sonatype.org"

replaceCommandAlias(
  "ci",
  "; project /; headerCheckAll; scalafmtCheckAll; scalafmtSbtCheck; clean; testIfRelevant; mimaReportBinaryIssuesIfRelevant"
)
replaceCommandAlias(
  "release",
  "; reload; project /; +mimaReportBinaryIssuesIfRelevant; +publishIfRelevant; sonatypeBundleRelease"
)
addCommandAlias("prePR", "; root/clean; +root/scalafmtAll; scalafmtSbt; +root/headerCreate")

val Scala3 = "3.0.1"
ThisBuild / crossScalaVersions := Seq(Scala3)

val CatsVersion = "2.6.1"
val CatsEffectVersion = "3.2.3"
val Fs2Version = "3.1.1"
val Http4sVersion = "1.0.0-M24"
val CirceVersion = "0.15.0-M1"
val Specs2Version = "5.0.0-RC-03"

val commonSettings = Seq(
  scalacOptions ++=
    Seq("-new-syntax", "-indent", "-source:future"),
  sonatypeCredentialHost := "s01.oss.sonatype.org"
)

lazy val root =
  project.aggregate(core.jvm, core.js).enablePlugins(NoPublishPlugin)

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .in(file("core"))
  .jsEnablePlugins(ScalaJSBundlerPlugin)
  .settings(
    name := "gcp4s",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % CatsVersion,
      "org.typelevel" %%% "cats-effect" % CatsEffectVersion,
      "co.fs2" %%% "fs2-core" % Fs2Version,
      "org.http4s" %%% "http4s-client" % Http4sVersion,
      "org.http4s" %%% "http4s-circe" % Http4sVersion,
      "io.circe" %%% "circe-generic" % CirceVersion,
      "org.specs2" %%% "specs2-core" % Specs2Version % Test
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.github.jwt-scala" %%% "jwt-circe" % "9.0.1"
    )
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-scalajs" % CirceVersion
    ),
    useYarn := true,
    yarnExtraArgs += "--frozen-lockfile",
    Compile / npmDependencies ++= Seq(
      "jsonwebtoken" -> "8.5.1"
    )
  )
  .settings(commonSettings)
