import cats.data.Writer
import cats.syntax.all._
import cats.instances.list._
import io.circe._
import io.circe.generic.auto._
import io.circe.yaml.parser._
import sbt.Keys._
import sbt._
import cats.Traverse

object OpenApiPlugin extends AutoPlugin {

  override def trigger = noTrigger

  object autoImport extends scala.AnyRef {
    lazy val openApiGenerate =
      taskKey[Unit]("Generate Scala case class for the given OpenApi model")
    lazy val openApiPackage = settingKey[String]("Package for generated sources")
    lazy val openApiComponents = settingKey[Seq[String]]("Components to generate sources for")
    lazy val openApiImports = settingKey[Seq[String]]("Additional imports")
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
    val api = yaml.as[openapi.OpenApi].fold(throw _, identity)

    val models = api
      .components
      .schemas
      .filterKeys(!Set("JsonObject", "JsonValue").contains(_))
      .toList
      .flatMap {
        case (name, schema) =>
          mkComponent(name, schema)
      }
      .map { caseClass =>
        val f =
          (Compile / sourceManaged).value / "openapi" / "model" / s"${caseClass.name}.scala"
        IO.write(
          f,
          s"""|package ${openApiPackage.value}.model
              |
              |import _root_.gcp4s.json.given
              |
              |$caseClass
              |""".stripMargin
        )
        f
      }

    models
  }

  def mkComponent(name: String, component: openapi.ComponentSchema): List[CaseClass] = {
    type F[A] = Writer[List[CaseClass], A]
    Traverse[List]
      .traverse[F, (String, openapi.Property), Parameter](
        component.properties.toList.flatMap(_.toList)) {
        case (propertyName, property) =>
          mkProperty(name, propertyName, property)
      }
      .flatMap { parameters => Writer.tell(CaseClass(name, parameters) :: Nil) }
      .written
  }

  def mkProperty(
      parentName: String,
      name: String,
      property: openapi.Property): Writer[List[CaseClass], Parameter] =
    mkPropertyType(parentName, name, property).map { t => Parameter(name, t, required = false) }

  def mkPropertyType(
      parentName: String,
      name: String,
      property: openapi.Property): Writer[List[CaseClass], String] = {
    val fmtOrType = property.format.orElse(property.`type`)

    val primitive = fmtOrType
      .collect {
        case "string" => "String"
        case "boolean" => "Boolean"
        case "integer" => "Int"
        case "int32" => "Int"
        case "int64" =>
          if (property.description.exists(_.contains("milliseconds")))
            "_root_.scala.concurrent.duration.FiniteDuration"
          else
            "Long"
        case "uint64" => "BigInt"
        case "double" => "Double"
        case "byte" => "_root_.scodec.bits.ByteVector"
      }
      .map(Writer(List.empty[CaseClass], _))

    val array = fmtOrType.collect {
      case "array" =>
        property.items.map { p =>
          mkPropertyType(parentName, name.capitalize, p).map { t => s"Vector[$t]" }
        }
    }.flatten

    val ref = property
      .`$ref`
      .map(_.split("/").last)
      .map {
        case "JsonValue" => "_root_.io.circe.Json"
        case "JsonObject" => "_root_.io.circe.JsonObject"
        case x => x
      }
      .map(Writer(List.empty[CaseClass], _))

    val obj = fmtOrType.collect {
      case "object" =>
        property
          .properties
          .map { p =>
            val componentName = s"$parentName${name.capitalize}"
            Writer(
              mkComponent(
                componentName,
                openapi.ComponentSchema(property.description, Some(p))),
              componentName
            )
          }
          .orElse(property.additionalProperties.map { p =>
            mkPropertyType(parentName, name.capitalize, p).map { t => s"Map[String, $t]" }
          })
          .getOrElse(Writer(List.empty[CaseClass], "_root_.io.circe.JsonObject"))
    }

    primitive
      .orElse(ref)
      .orElse(array)
      .orElse(obj)
      .getOrElse(Writer(Nil, "_root_.io.circe.Json"))
  }

}
