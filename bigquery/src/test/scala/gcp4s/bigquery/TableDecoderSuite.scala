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

import gcp4s.bigquery.model.TableRow
import io.circe.parser
import munit.FunSuite

class TableDecoderSuite extends FunSuite:
  val json = """{
               |  "f": [
               |    {
               |      "v": "Peter"
               |    },
               |    {
               |      "v": [
               |        {
               |          "v": {
               |            "f": [
               |              {
               |                "v": "street1"
               |              },
               |              {
               |                "v": "city1"
               |              }
               |            ]
               |          }
               |        },
               |        {
               |          "v": {
               |            "f": [
               |              {
               |                "v": "street2"
               |              },
               |              {
               |                "v": "city2"
               |              }
               |            ]
               |          }
               |        }
               |      ]
               |    }
               |  ]
               |}
               |""".stripMargin

  case class Record(name: Option[String], addresses: Vector[Address]) derives TableRowDecoder
  case class Address(street: Option[String], city: Option[String]) derives TableRowDecoder

  test("TableDecoder understands BigQuery") {
    assertEquals(
      parser.decode[TableRow](json).flatMap(TableRowDecoder[Record].decode),
      Right(
        Record(
          Some("Peter"),
          Vector(
            Address(Some("street1"), Some("city1")),
            Address(Some("street2"), Some("city2")))))
    )
  }
