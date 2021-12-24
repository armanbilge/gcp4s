package sbtbuildinfo

final case class Scala3CaseObjectRenderer(
    options: Seq[BuildInfoOption],
    pkg: String,
    obj: String)
    extends ScalaRenderer {
  private lazy val delegate = ScalaCaseObjectRenderer(options, pkg, obj)

  def extension: String = delegate.extension
  def fileType: BuildInfoType = delegate.fileType
  def renderKeys(infoKeysNameAndValues: Seq[BuildInfoResult]): Seq[String] =
    delegate.renderKeys(infoKeysNameAndValues).map {
      case "import scala.Predef._" => "import scala.Predef.*"
      case x => x
    }
}
