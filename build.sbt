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

val Scala3 = "3.0.2"
ThisBuild / crossScalaVersions := Seq(Scala3)

val CatsVersion = "2.6.1"
val CatsEffectVersion = "3.2.8"
val Fs2Version = "3.1.2"
val Http4sVersion = "1.0.0-M25"
val CirceVersion = "0.15.0-M1"
val MonocleVersion = "3.1.0"
val MunitVersion = "0.7.29"
val MunitCE3Version = "1.0.5"
val ScalaCheckEffectMunitVersion = "1.0.2"
val ShapelessVersion = "3.0.2"

val commonSettings = Seq(
  scalacOptions ++=
    Seq("-new-syntax", "-indent", "-source:future", "-Xmax-inlines", "64"),
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
      "co.fs2" %%% "fs2-io" % Fs2Version,
      "org.http4s" %%% "http4s-client" % Http4sVersion,
      "org.http4s" %%% "http4s-circe" % Http4sVersion,
      "io.circe" %%% "circe-parser" % CirceVersion,
      "io.circe" %%% "circe-scodec" % CirceVersion,
      "org.scalameta" %%% "munit" % MunitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect-3" % MunitCE3Version % Test,
      "org.typelevel" %%% "scalacheck-effect-munit" % ScalaCheckEffectMunitVersion % Test
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.github.jwt-scala" %%% "jwt-circe" % "9.0.1"
    )
  )
  .jsSettings(
    useYarn := true,
    yarnExtraArgs += "--frozen-lockfile",
    Compile / npmDependencies ++= Seq(
      "jsonwebtoken" -> "8.5.1"
    )
  )
  .settings(commonSettings)

lazy val bigQuery = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("bigquery"))
  .enablePlugins(OpenApiPlugin)
  .settings(
    openApiPackage := "gcp4s.bigquery",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "shapeless3-deriving" % ShapelessVersion,
      "dev.optics" %%% "monocle-core" % MonocleVersion,
      "org.scalameta" %%% "munit" % MunitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect-3" % MunitCE3Version % Test,
      "org.typelevel" %%% "scalacheck-effect-munit" % ScalaCheckEffectMunitVersion % Test
    )
  )
  .settings(commonSettings)
  .dependsOn(core)
