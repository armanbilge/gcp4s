package openapi

case class OpenApi(servers: List[Server], paths: Map[String, Path], components: Components)

case class Server(url: String)

case class Path(
    delete: Option[Operation],
    get: Option[Operation],
    patch: Option[Operation],
    post: Option[Operation],
    put: Option[Operation]
) {
  def all: List[Operation] =
    delete.toList ::: get.toList ::: patch.toList ::: post.toList ::: put.toList ::: Nil

  def toMap: Map[String, Operation] =
    delete.map("DELETE" -> _).toMap ++ get.map("GET" -> _).toMap ++ patch
      .map("PATCH" -> _)
      .toMap ++ post.map("POST" -> _).toMap ++ post.map("PUT" -> _).toMap
}

case class Operation(
    description: String,
    operationId: String,
    parameters: Vector[Parameter],
    responses: Map[String, Response],
    tags: Vector[String]
)

case class Parameter(
    description: String,
    in: String,
    name: String,
    required: Option[Boolean],
    schema: ParameterSchema
)

case class ParameterSchema(`type`: String, format: Option[String])

case class Response(content: Option[Content])
case class Content(`application/json`: `Application/Json`)
case class `Application/Json`(schema: Ref)
case class Ref(`$ref`: String)

case class Components(schemas: Map[String, ComponentSchema])
case class ComponentSchema(
    description: Option[String],
    properties: Option[Map[String, Property]]
)
case class Property(
    description: Option[String],
    format: Option[String],
    `type`: Option[String],
    `$ref`: Option[String],
    properties: Option[Map[String, Property]],
    additionalProperties: Option[Property],
    items: Option[Property]
)
