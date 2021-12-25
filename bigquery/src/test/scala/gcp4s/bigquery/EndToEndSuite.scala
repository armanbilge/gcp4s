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

package gcp4s
package bigquery

import cats.effect.IO
import cats.effect.std.UUIDGen
import cats.syntax.all.*
import fs2.Stream
import gcp4s.bigquery.model.*
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import scodec.bits.ByteVector
import io.circe.Codec
import scala.concurrent.duration.*
import gcp4s.json.given
import gcp4s.bigquery.syntax.*

import java.util.UUID

case class A(
    integer: Int,
    long: Long,
    float: Float,
    double: Double,
    string: String,
    boolean: Boolean,
    record: B
) derives TableSchemaEncoder,
      TableRowDecoder,
      Codec.AsObject
given Arbitrary[A] = Arbitrary(
  for
    i <- Arbitrary.arbitrary[Int]
    l <- Arbitrary.arbitrary[Long]
    f <- Arbitrary.arbitrary[Float]
    d <- Arbitrary.arbitrary[Double]
    s <- Arbitrary.arbitrary[String]
    b <- Arbitrary.arbitrary[Boolean]
    r <- Arbitrary.arbitrary[B]
  yield A(i, l, f, d, s, b, r))

case class B(nullable: Option[String], bytes: ByteVector, repeated: Vector[C])
    derives TableFieldSchemaEncoder,
      TableRowDecoder,
      Codec.AsObject
given Arbitrary[B] = Arbitrary(
  for
    n <- Arbitrary.arbitrary[Option[String]]
    b <- Arbitrary.arbitrary[Array[Byte]].map(ByteVector.view)
    r <- Arbitrary.arbitrary[Vector[C]]
  yield B(n, b, r))

case class C(numeric: BigDecimal)
    derives TableFieldSchemaEncoder,
      TableRowDecoder,
      Codec.AsObject
given Arbitrary[C] = Arbitrary(Gen.choose(-1e-28, 1e28).map(d => C(BigDecimal(d))))

class EndToEndSuite extends Gcp4sLiveSuite {

  val bq = googleClient.map(BigQueryClient[IO](_))

  test("projects contains project id") {
    bq.flatMap { bq =>
      for
        projectList <- bq.projects.list.compile.toVector
        projectIds = projectList.flatMap(_.projects.toVector.flatten).flatMap(_.id)
        creds <- googleCredentials
      yield assert(projectIds.contains(creds.projectId))
    }
  }

  test("end-to-end") {
    bq.flatMap { bq =>
      for
        projectId <- googleCredentials.map(_.projectId)
        projectRef = ProjectReference(projectId = projectId.some)
        datasetId <- UUIDGen[IO].randomUUID.map { uuid =>
          ByteVector(uuid.getMostSignificantBits, uuid.getLeastSignificantBits).toHex
        }
        tableId <- UUIDGen[IO].randomUUID.map { uuid =>
          ByteVector(uuid.getMostSignificantBits, uuid.getLeastSignificantBits).toHex
        }
        datasetRef = DatasetReference(projectId = projectId.some, datasetId = datasetId.some)
        dataset <- bq
          .datasets
          .insert(
            Dataset(datasetReference = datasetRef.some, defaultTableExpirationMs = 1.hour.some))
        _ <- IO(dataset.datasetReference.contains(datasetRef)).assert
        _ <- bq
          .datasets
          .list(projectRef)
          .map(_.datasetReference)
          .flatMap(Stream.fromOption(_))
          .compile
          .toVector
          .map(_.contains(datasetRef))
          .assert
        tableRef = TableReference(
          projectId = projectId.some,
          datasetId = datasetId.some,
          tableId = tableId.some)
        table <- bq
          .tables
          .insert(Table(tableReference = tableRef.some, schema = schemaFor[A].some))
        _ <- IO(table.tableReference.contains(tableRef)).assert
        _ <- bq
          .tables
          .list(datasetRef)
          .map(_.tableReference)
          .flatMap(Stream.fromOption(_))
          .compile
          .toVector
          .map(_.contains(tableRef))
          .assert
        rows <- IO(Arbitrary.arbitrary[Vector[A]].sample.get)
        loadJob = Job(
          jobReference = JobReference(projectId = projectId.some).some,
          configuration = JobConfiguration(
            load = JobConfigurationLoad(destinationTable = tableRef.some).some
          ).some
        )
        _ <- Stream
          .emits(rows)
          .through(bq.jobs.uploadAs(loadJob))
          .flatMap { job =>
            Stream
              .fromOption(job.jobReference)
              .evalMap(bq.jobs.get(_))
              .repeat
              .metered(1.second)
              .find(_.status.flatMap(_.state).contains("DONE"))
              .foreach(j => IO(j.status.flatMap(_.errorResult).isEmpty).assert)
          }
          .compile
          .drain
        _ <- bq.tableData.listAs[A](tableRef).compile.toVector.map(_.toSet == rows.toSet)
        _ <- bq.tables.delete(tableRef)
        _ <- bq
          .tables
          .list(datasetRef)
          .map(_.tableReference)
          .flatMap(Stream.fromOption(_))
          .compile
          .toVector
          .map(!_.contains(tableRef))
          .assert
        _ <- bq.datasets.delete(datasetRef)
        _ <- bq
          .datasets
          .list(projectRef)
          .map(_.datasetReference)
          .flatMap(Stream.fromOption(_))
          .compile
          .toVector
          .map(!_.contains(datasetRef))
          .assert
      yield ()
    }
  }

}
