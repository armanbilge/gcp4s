import cats.data.Writer
import cats.syntax.all._
import cats.instances.list._
import io.circe._
import io.circe.generic.auto._
import sbt.Keys._
import sbt._
import cats.Traverse
import org.jboss.dna.common.text.Inflector

object DiscoveryPlugin extends AutoPlugin {

  override def trigger = noTrigger

  object autoImport extends scala.AnyRef {
    lazy val discoveryGenerate =
      taskKey[Unit]("Generate Scala case classes for the given Discovery Document")
    lazy val discoveryPackage = settingKey[String]("Package for generated sources")
  }
  import autoImport._

  override val projectSettings = Seq(
    discoveryGenerate := discoveryTask.value,
    Compile / sourceGenerators += discoveryTask.taskValue
  )

  lazy val discoveryTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val f = (Compile / sourceDirectories)
      .value
      .map(_.getParentFile / "discovery" / "discovery.json")
      .find(_.exists())
      .get
    val discovery = parser.decode[Discovery](IO.read(f)).fold(throw _, identity)

    val models = discovery
      .schemas
      .filterKeys(!Set("JsonObject", "JsonValue").contains(_))
      .toList
      .flatMap {
        case (name, schema) =>
          mkSchema(name, schema)
      }
      .map { caseClass =>
        val f =
          (Compile / sourceManaged).value / "discovery" / "model" / s"${caseClass.name}.scala"
        IO.write(
          f,
          s"""|package ${discoveryPackage.value}.model
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

  def mkSchema(name: String, schema: Schema): List[CaseClass] = {
    type F[A] = Writer[List[CaseClass], A]
    Traverse[List]
      .traverse[F, (String, Schema), Parameter](schema.properties.toList.flatMap(_.toList)) {
        case (propertyName, property) =>
          mkProperty(name, propertyName, property)
      }
      .flatMap { parameters => Writer.tell(CaseClass(name, parameters) :: Nil) }
      .written
  }

  def mkProperty(
      parentName: String,
      name: String,
      property: Schema): Writer[List[CaseClass], Parameter] =
    mkPropertyType(parentName, name, property).map { t => Parameter(name, t, required = false) }

  def mkPropertyType(
      parentName: String,
      name: String,
      property: Schema): Writer[List[CaseClass], String] = {

    val primitive = property
      .format
      .collect {
        case "int32" => "Int"
        case "int64" | "uint32" =>
          if (property.description.exists(_.contains("millis")))
            "_root_.scala.concurrent.duration.FiniteDuration"
          else
            "Long"
        case "uint64" => "BigInt"
        case "double" => "Double"
        case "byte" => "_root_.scodec.bits.ByteVector"
      }
      .orElse(property.`enum`.map { enums => enums.map('"' + _ + '"').mkString(" | ") })
      .orElse(property.`type`.collect {
        case "string" => "String"
        case "boolean" => "Boolean"
        case "integer" => "Int"
        case "number" => "Double"
      })
      .map(Writer(List.empty[CaseClass], _))

    val array = property
      .`type`
      .collect {
        case "array" =>
          property.items.map { p =>
            mkPropertyType(parentName, inflector.singularize(name).capitalize, p).map { t =>
              s"Vector[$t]"
            }
          }
      }
      .flatten

    val ref = property
      .`$ref`
      .map(_.split("/").last)
      .map {
        case "JsonValue" => "_root_.io.circe.Json"
        case "JsonObject" => "_root_.io.circe.JsonObject"
        case x => x
      }
      .map(Writer(List.empty[CaseClass], _))

    val obj = property.`type`.collect {
      case "object" =>
        property
          .properties
          .map { p =>
            val schemaName = s"$parentName${name.capitalize}"
            Writer(
              mkSchema(schemaName, Schema(properties = Some(p))),
              schemaName
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

  private val inflector = new Inflector()

}
