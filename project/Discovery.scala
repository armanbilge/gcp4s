case class Discovery(schemas: Map[String, Schema])
case class Schema(
    description: Option[String] = None,
    `type`: Option[String] = None,
    format: Option[String] = None,
    $ref: Option[String] = None,
    properties: Option[Map[String, Schema]] = None,
    additionalProperties: Option[Schema] = None,
    items: Option[Schema] = None,
    `enum`: Option[List[String]] = None)
