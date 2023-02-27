ThisBuild / tlBaseVersion := "0.1"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers += tlGitHubDev("armanbilge", "Arman Bilge")
ThisBuild / startYear := Some(2021)

ThisBuild / tlUntaggedAreSnapshots := false
ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / githubWorkflowEnv += "SERVICE_ACCOUNT_CREDENTIALS" -> "${{ secrets.SERVICE_ACCOUNT_CREDENTIALS }}"

val Scala3 = "3.2.2"
ThisBuild / crossScalaVersions := Seq(Scala3)

val CatsVersion = "2.9.0"
val CatsEffectVersion = "3.4.6"
val CirceVersion = "0.14.3"
val Fs2Version = "3.5.0"
val Http4sVersion = "0.23.18"
val Log4CatsVersion = "2.5.0"
val MonocleVersion = "3.2.0"
val MunitVersion = "0.7.29"
val MunitCE3Version = "1.0.7"
val NatchezVersion = "0.1.6"
val ScalaCheckEffectMunitVersion = "1.0.4"
val ScodecBitsVersion = "1.1.37"
val ShapelessVersion = "3.3.0"

ThisBuild / scalacOptions ++=
  Seq("-new-syntax", "-indent", "-source:future", "-Xmax-inlines", "64")

val commonJVMSettings = Seq(
  fork := true
)
val commonJSSettings = Seq(
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
)

lazy val root = tlCrossRootProject.aggregate(core, bigQuery, trace)

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .in(file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "gcp4s",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % CatsVersion,
      "org.typelevel" %%% "cats-effect" % CatsEffectVersion,
      "co.fs2" %%% "fs2-io" % Fs2Version,
      "org.http4s" %%% "http4s-client" % Http4sVersion,
      "org.http4s" %%% "http4s-circe" % Http4sVersion,
      "io.circe" %%% "circe-jawn" % CirceVersion,
      "io.circe" %%% "circe-scodec" % CirceVersion,
      "org.scodec" %%% "scodec-bits" % ScodecBitsVersion,
      "org.scalameta" %%% "munit" % MunitVersion % Test,
      "org.typelevel" %%% "munit-cats-effect-3" % MunitCE3Version % Test,
      "org.typelevel" %%% "scalacheck-effect-munit" % ScalaCheckEffectMunitVersion % Test,
      "org.http4s" %%% "http4s-dsl" % Http4sVersion % Test,
      "org.http4s" %%% "http4s-ember-client" % Http4sVersion % Test
    ),
    buildInfoPackage := "gcp4s",
    buildInfoOptions += BuildInfoOption.PackagePrivate
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.11" % Test
    )
  )
  .jvmSettings(commonJVMSettings)
  .jsSettings(commonJSSettings)

lazy val bigQuery = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("bigquery"))
  .enablePlugins(DiscoveryPlugin)
  .settings(
    name := "gcp4s-bigquery",
    discoveryPackage := "gcp4s.bigquery",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "shapeless3-deriving" % ShapelessVersion,
      "dev.optics" %%% "monocle-core" % MonocleVersion
    )
  )
  .jvmSettings(commonJVMSettings)
  .jsSettings(commonJSSettings)
  .dependsOn(core % "compile->compile;test->test")

lazy val trace = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("trace"))
  .enablePlugins(DiscoveryPlugin)
  .settings(
    name := "gcp4s-trace",
    discoveryPackage := "gcp4s.trace",
    libraryDependencies ++= Seq(
      "org.tpolecat" %%% "natchez-core" % NatchezVersion,
      "org.typelevel" %%% "log4cats-core" % Log4CatsVersion
    )
  )
  .jvmSettings(commonJVMSettings)
  .jsSettings(commonJSSettings)
  .dependsOn(core % "compile->compile;test->test")
