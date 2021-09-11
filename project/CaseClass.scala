case class CaseClass(
    name: String,
    parameters: List[Parameter]
) {
  override def toString: String = {
    val name = Sanitize(this.name)
    s"""|final case class $name(
        |${parameters.map("  " + _).mkString(",\n")}
        |) derives _root_.io.circe.Decoder, _root_.io.circe.Encoder.AsObject
        |""".stripMargin
  }
}

case class Parameter(
    name: String,
    `type`: String,
    required: Boolean
) {
  override def toString: String =
    if (required)
      s"${Sanitize(name)}: ${`type`}"
    else
      s"${Sanitize(name)}: Option[${`type`}] = None"
}

object Sanitize {
  def apply(s: String): String = s match {
    case "type" => "`type`"
    case s => s
  }
}
