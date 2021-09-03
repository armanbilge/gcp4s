libraryDependencies ++= {
  val circeVersion = "0.14.1"
  Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-yaml" % circeVersion
  )
}
