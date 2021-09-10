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

import cats.ApplicativeThrow
import cats.syntax.all.*
import gcp4s.bigquery.model.TableCell
import gcp4s.bigquery.model.TableRow
import io.circe.Decoder
import scodec.bits.ByteVector

trait TableRowDecoder[A]:
  def decode(row: TableRow): Either[Throwable, A]

object TableRowDecoder

trait TableCellDecoder[A]:
  def decode(cell: TableCell): Either[Throwable, A]

object TableCellDecoder:

  inline def apply[A](using d: TableCellDecoder[A]): d.type = d

  private def viaString[A](d: String => Either[Throwable, A]): TableCellDecoder[A] =
    _.v.toRight(new NoSuchElementException).flatMap(_.as[String]).flatMap(d)

  private val F = ApplicativeThrow[Either[Throwable, *]]

  given TableCellDecoder[String] = viaString(Either.right)
  given TableCellDecoder[Int] = viaString(s => F.catchNonFatal(s.toInt))
  given TableCellDecoder[Long] = viaString(s => F.catchNonFatal(s.toLong))
  given TableCellDecoder[Double] = viaString(s => F.catchNonFatal(s.toDouble))
  given TableCellDecoder[ByteVector] = viaString(
    ByteVector.fromBase64Descriptive(_).leftMap(new RuntimeException(_)))

  given TableCellDecoder[TableRow] with
    def decode(cell: TableCell) =
      cell.v.toRight(new NoSuchElementException).flatMap(_.as[TableRow])

  given [A](using d: TableRowDecoder[A]): TableCellDecoder[A] with
    def decode(cell: TableCell) = TableCellDecoder[TableRow].decode(cell).flatMap(d.decode)

  given [A](using d: TableCellDecoder[A]): TableCellDecoder[Vector[A]] with
    def decode(cell: TableCell) = cell
      .v
      .toRight(new NoSuchElementException)
      .flatMap(_.as[Vector[TableCell]])
      .flatMap(_.traverse(d.decode))
