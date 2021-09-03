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

    val resources = api
      .paths
      .groupBy(_._2.all.head.operationId.split('.').init.last)
      .map {
        case (resource, operations) =>
          val f =
            (Compile / sourceManaged).value / "openapi" / "api" / s"${resource.capitalize}.scala"
          IO.write(
            f,
            mkResource(
              openApiPackage.value,
              resource.capitalize,
              operations.flatMap {
                case (path, operations) =>
                  operations.toMap.map {
                    case (method, operation) =>
                      (path, method, operation)
                  }
              }.toVector
            )
          )
          f
      }
      .toVector

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

    resources ++ models
  }

  def mkComponent(pkg: String, name: String, component: ComponentSchema): String = {
    val properties = component.properties.map {
      
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
        |
        )
        |""".stripMargin
  }

  def mkProperty(name: String, property: Property): String =
    s"""${name}: Option[${mkPropertyType(property)}] = None""".stripMargin

  def mkPropertyType(property: Property): String =
    property.format.getOrElse(property.`type`) match {
      case _ if property.description.contains("milliseconds") =>
        "scala.concurrent.duration.FiniteDuration"
      case "string" => "String"
      case "boolean" => "Boolean"
      case "integer" => "Int"
      case "int64" => "Long"
    }

}
