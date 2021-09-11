/*
 * Copyright 2021 Arman Bilge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gcp4s.bigquery

import cats.syntax.all.*
import gcp4s.bigquery.model.TableFieldSchema
import gcp4s.bigquery.model.TableSchema
import munit.FunSuite

class TableSchemaEncoderSuite extends FunSuite:

  case class A(
      integer: Int,
      long: Long,
      float: Float,
      double: Double,
      string: String,
      boolean: Boolean,
      record: B)
      derives TableSchemaEncoder
  case class B(nullable: Option[String], repeated: Vector[C]) derives TableFieldSchemaEncoder
  case class C(numeric: BigDecimal) derives TableFieldSchemaEncoder

  def field(n: String, t: String, m: String = "REQUIRED", f: TableFieldSchema*) =
    TableFieldSchema(
      name = n.some,
      `type` = t.some,
      mode = m.some,
      fields = Option.when(t == "RECORD")(f.toVector))

  val schema = TableSchema(
    Vector(
      field("integer", "INTEGER"),
      field("long", "INTEGER"),
      field("float", "FLOAT"),
      field("double", "FLOAT"),
      field("string", "STRING"),
      field("boolean", "BOOLEAN"),
      field(
        "record",
        "RECORD",
        "REQUIRED",
        field("nullable", "STRING", "NULLABLE"),
        field("repeated", "RECORD", "REPEATED", field("numeric", "NUMERIC"))
      )
    ).some
  )

  test("TableSchemaEncoder generates schema") {
    assertEquals(TableSchemaEncoder[A].encode, schema)
  }
