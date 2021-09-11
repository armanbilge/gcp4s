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
import scodec.bits.ByteVector
import shapeless3.deriving
import shapeless3.deriving.K0
import shapeless3.deriving.Labelling

trait TableSchemaEncoder[A]:
  def encode: TableSchema

object TableSchemaEncoder:
  inline def apply[A](using e: TableSchemaEncoder[A]): e.type = e

  inline def derived[A](
      using gen: K0.ProductGeneric[A],
      labelling: Labelling[A]): TableSchemaEncoder[A] =
    new TableSchemaEncoder[A]:
      def encode = TableSchema(derivedFields[A].some)

trait TableFieldSchemaEncoder[A]:
  def encode(name: String): TableFieldSchema

object TableFieldSchemaEncoder:

  inline def derived[A](
      using gen: K0.ProductGeneric[A],
      labelling: Labelling[A]): TableFieldSchemaEncoder[A] =
    new TableFieldSchemaEncoder[A]:
      def encode(name: String) = TableFieldSchema(
        name = name.some,
        `type` = "RECORD".some,
        fields = derivedFields[A].some,
        mode = "REQUIRED".some)

  final private class Primitive[A](`type`: String) extends TableFieldSchemaEncoder[A]:
    final def encode(name: String) =
      TableFieldSchema(name = name.some, `type` = `type`.some, mode = "REQUIRED".some)

  given TableFieldSchemaEncoder[String] = Primitive("STRING")
  given TableFieldSchemaEncoder[ByteVector] = Primitive("BYTES")
  given TableFieldSchemaEncoder[Int] = Primitive("INTEGER")
  given TableFieldSchemaEncoder[Long] = Primitive("INTEGER")
  given TableFieldSchemaEncoder[BigDecimal] = Primitive("NUMERIC")
  given TableFieldSchemaEncoder[Float] = Primitive("FLOAT")
  given TableFieldSchemaEncoder[Double] = Primitive("FLOAT")
  given TableFieldSchemaEncoder[Boolean] = Primitive("BOOLEAN")

  given [A](using e: TableFieldSchemaEncoder[A]): TableFieldSchemaEncoder[Option[A]] with
    def encode(name: String) = e.encode(name).copy(mode = "NULLABLE".some)

  given [A](using e: TableFieldSchemaEncoder[A]): TableFieldSchemaEncoder[Vector[A]] with
    def encode(name: String) = e.encode(name).copy(mode = "REPEATED".some)

private[bigquery] inline def derivedFields[A](
    using gen: K0.ProductGeneric[A],
    labelling: Labelling[A]): Vector[TableFieldSchema] =
  val encoders =
    deriving.summonAsArray[K0.LiftP[TableFieldSchemaEncoder, gen.MirroredElemTypes]]
  encoders
    .lazyZip(labelling.elemLabels)
    .map(_.asInstanceOf[TableFieldSchemaEncoder[Any]].encode(_))
    .toVector
