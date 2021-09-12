resolvers += "JBoss releases" at "https://repository.jboss.org/nexus/content/repositories/releases/"
libraryDependencies ++= {
  val circeVersion = "0.14.1"
  Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-yaml" % circeVersion,
    "org.jboss.dna" % "dna-common" % "0.7"
  )
}
