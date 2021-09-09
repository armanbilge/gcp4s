import io.circe._
import io.circe.generic.auto._
import io.circe.yaml.parser._
import sbt.Keys._
import sbt._

object OpenApiPlugin extends AutoPlugin {

  override def trigger = noTrigger

  object autoImport extends scala.AnyRef {
    lazy val openApiGenerate =
      taskKey[Unit]("Generate Scala case class for the given OpenApi model")
    lazy val openApiPackage = settingKey[String]("Package for generated sources")
  }
  import autoImport._

  override val projectSettings = Seq(
    openApiGenerate := openApiTask.value,
    Compile / sourceGenerators += openApiTask.taskValue
  )

  lazy val openApiTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val f = (Compile / sourceDirectories)
      .value
      .map(_.getParentFile / "openapi" / "openapi.yaml")
      .find(_.exists())
      .get
    val yaml = parse(IO.read(f)).fold(throw _, identity)
    val api = yaml.as[OpenApi].fold(throw _, identity)

    val models = api
      .components
      .schemas
      .map {
        case (name, schema) =>
          val f = (Compile / sourceManaged).value / "openapi" / "model" / s"${name}.scala"
          IO.write(f, mkComponent(openApiPackage.value, name, schema))
          f
      }
      .toVector

    models
  }

  def mkComponent(pkg: String, name: String, component: ComponentSchema): String = {
    val properties = component.properties.fold("") { properties =>
      properties.toList.map { case (name, property) =>
        mkProperty(name, property).toList
      }.flatten.map("  " + _).mkString(",\n")
    }
    
    s"""|package ${pkg}.model
        |
        |import io.circe.Decoder
        |import io.circe.Encoder
        |import io.circe.generic.semiauto.*
        |
        |/** ${component.description.getOrElse("")}
        | */
        |final case class ${name}(
        |${properties}
        |)
        |""".stripMargin
  }

  def mkProperty(name: String, property: Property): Option[String] =
    mkPropertyType(property).map { `type` =>
      s"""${name}: Option[${`type`}] = None""".stripMargin
    }

  def mkPropertyType(property: Property): Option[String] =
    property.format.orElse(property.`type`).collect {
      case _ if property.description.exists(_.contains("milliseconds")) =>
        "scala.concurrent.duration.FiniteDuration".some
      case "string" => "String".some
      case "boolean" => "Boolean".some
      case "integer" => "Int".some
      case "int32" => "Int".some
      case "int64" => "Long".some
      case "double" => "Double".some
      case "array" => 
        property.items.flatMap(mkPropertyType).map { `type` =>
          s"Vector[${`type`}]"
        }
      case "object" =>
        property.`$ref`.orElse("io.circe.Json".some)
    }.flatten

}
