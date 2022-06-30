ThisBuild / tlBaseVersion := "0.1"

ThisBuild / organization := "com.armanbilge"
ThisBuild / organizationName := "Arman Bilge"
ThisBuild / developers += tlGitHubDev("armanbilge", "Arman Bilge")
ThisBuild / startYear := Some(2021)

ThisBuild / tlUntaggedAreSnapshots := false
ThisBuild / tlSonatypeUseLegacyHost := false
ThisBuild / tlCiReleaseBranches += "topic/2.13"

ThisBuild / githubWorkflowEnv += "SERVICE_ACCOUNT_CREDENTIALS" -> "${{ secrets.SERVICE_ACCOUNT_CREDENTIALS }}"

val Scala3 = "3.1.2"
ThisBuild / crossScalaVersions := Seq(Scala3)

val CatsVersion = "2.7.0"
val CatsEffectVersion = "3.3.12"
val CirceVersion = "0.14.2"
val Fs2Version = "3.2.7"
val Http4sVersion = "0.23.12"
val Log4CatsVersion = "2.3.1"
val MonocleVersion = "3.1.0"
val MunitVersion = "0.7.29"
val MunitCE3Version = "1.0.7"
val NatchezVersion = "0.1.6"
val ScalaCheckEffectMunitVersion = "1.0.4"
val ScodecBitsVersion = "1.1.34"
val ShapelessVersion = "3.1.0"

ThisBuild / scalacOptions ++=
  Seq("-new-syntax", "-indent", "-source:future", "-Xmax-inlines", "64")

val commonJVMSettings = Seq(
  fork := true
)
val commonJSSettings = Seq(
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
)

lazy val root = tlCrossRootProject.aggregate(core)

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .in(file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "gcp4s",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % CatsVersion cross CrossVersion.for3Use2_13,
      "org.typelevel" %%% "cats-effect" % CatsEffectVersion cross CrossVersion.for3Use2_13,
      "co.fs2" %%% "fs2-io" % Fs2Version cross CrossVersion.for3Use2_13,
      "org.http4s" %%% "http4s-client" % Http4sVersion cross CrossVersion.for3Use2_13,
      "org.http4s" %%% "http4s-circe" % Http4sVersion cross CrossVersion.for3Use2_13,
      "io.circe" %%% "circe-jawn" % CirceVersion cross CrossVersion.for3Use2_13,
      "io.circe" %%% "circe-scodec" % CirceVersion cross CrossVersion.for3Use2_13,
      "org.scodec" %%% "scodec-bits" % ScodecBitsVersion cross CrossVersion.for3Use2_13,
      "org.scalameta" %%% "munit" % MunitVersion % Test cross CrossVersion.for3Use2_13,
      "org.typelevel" %%% "munit-cats-effect-3" % MunitCE3Version % Test cross CrossVersion.for3Use2_13,
      "org.typelevel" %%% "scalacheck-effect-munit" % ScalaCheckEffectMunitVersion % Test cross CrossVersion.for3Use2_13,
      "org.http4s" %%% "http4s-dsl" % Http4sVersion % Test cross CrossVersion.for3Use2_13,
      "org.http4s" %%% "http4s-ember-client" % Http4sVersion % Test cross CrossVersion.for3Use2_13
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

val `drive-client` = crossProject(JVMPlatform, JSPlatform)
  .in(file("google-drive"))
  .enablePlugins(DiscoveryPlugin)
  .settings(
    name := "gcp4s-drive",
    discoveryPackage := "gcp4s.drive",
    crossScalaVersions := Seq("2.13.8"),
    scalacOptions := (scalacOptions.value.drop((ThisBuild / scalacOptions).value.size)),
    scalacOptions ++= Seq("-Ytasty-reader"),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % "0.14.2" cross CrossVersion.for3Use2_13
    )
  )
  .dependsOn(core % "compile->compile;test->test")

// lazy val bigQuery = crossProject(JVMPlatform, JSPlatform)
//   .crossType(CrossType.Pure)
//   .in(file("bigquery"))
//   .enablePlugins(DiscoveryPlugin)
//   .settings(
//     name := "gcp4s-bigquery",
//     discoveryPackage := "gcp4s.bigquery",
//     libraryDependencies ++= Seq(
//       "org.typelevel" %%% "shapeless3-deriving" % ShapelessVersion,
//       "dev.optics" %%% "monocle-core" % MonocleVersion
//     )
//   )
//   .jvmSettings(commonJVMSettings)
//   .jsSettings(commonJSSettings)
//   .dependsOn(core % "compile->compile;test->test")

// lazy val trace = crossProject(JVMPlatform, JSPlatform)
//   .crossType(CrossType.Pure)
//   .in(file("trace"))
//   .enablePlugins(DiscoveryPlugin)
//   .settings(
//     name := "gcp4s-trace",
//     discoveryPackage := "gcp4s.trace",
//     libraryDependencies ++= Seq(
//       "org.tpolecat" %%% "natchez-core" % NatchezVersion,
//       "org.typelevel" %%% "log4cats-core" % Log4CatsVersion
//     )
//   )
//   .jvmSettings(commonJVMSettings)
//   .jsSettings(commonJSSettings)
//   .dependsOn(core % "compile->compile;test->test")
