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

import cats.data.NonEmptyVector
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Ref
import cats.effect.kernel.Temporal
import cats.effect.std.Queue
import cats.effect.std.Random
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import fs2.text
import gcp4s.bigquery.model.*
import io.circe.Encoder
import io.circe.syntax.*
import monocle.syntax.all.*
import org.http4s.MediaType
import org.http4s.Method.*
import org.http4s.Uri
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.syntax.all.*

import scala.concurrent.duration.*

object BigQueryDsl:
  def apply[F[_]: Concurrent](client: Client[F]) = new BigQueryDsl(client) {}

trait BigQueryDsl[F[_]](client: Client[F])(using F: Concurrent[F]) extends Http4sClientDsl[F]:

  val endpoint = uri"https://bigquery.googleapis.com/bigquery/v2/"
  val mediaEndpoint = uri"https://bigquery.googleapis.com/upload/bigquery/v2/"

  def projects: Stream[F, ProjectList] =
    client.pageThrough(GET(endpoint / "projects"))

  extension (project: ProjectReference)
    def selfUri: Uri = endpoint / "projects" / project.projectId.getOrElse("")
    def mediaUri: Uri = mediaEndpoint / "projects" / project.projectId.getOrElse("")

    def datasets(
        all: Option[Boolean] = None,
        filter: Option[Map[String, String]]): Stream[F, DatasetList] =
      client.pageThrough(GET(selfUri / "datasets" +?? ("all" -> all) +?? ("filter" -> filter)))

    def jobs(
        allUsers: Option[Boolean] = None,
        maxCreationTime: Option[FiniteDuration],
        minCreationTime: Option[FiniteDuration],
        parentJobId: Option[JobReference],
        projection: Option["full" | "minimal"],
        stateFilter: Option[List["done" | "pending" | "running"]]
    ): Stream[F, JobList] =
      client.pageThrough(
        GET(
          selfUri / "jobs" +?? ("allUsers" -> allUsers) +?? ("maxCreationTime" -> maxCreationTime) +?? ("minCreationTime" -> minCreationTime) +?? ("parentJobId" -> parentJobId
            .flatMap(_.jobId)) +?? ("projection" -> projection
            .widen[String]) ++? ("stateFilter" -> stateFilter
            .widen[Seq[String]]
            .getOrElse(Seq.empty))
        )
      )

  extension (dataset: DatasetReference)
    def selfUri: Uri =
      ProjectReference(dataset.projectId).selfUri / "datasets" / dataset.datasetId.getOrElse("")

    def get: F[Dataset] = client.expect(GET(selfUri))
    def delete(deleteContents: Option[Boolean] = None): F[Unit] = client.expect(DELETE(selfUri))

  extension (dataset: Dataset)
    def selfUri: Uri = dataset.datasetReference.get.selfUri

    def insert: F[Dataset] = client.expect(POST(dataset, selfUri))
    def patch: F[Dataset] = client.expect(PATCH(dataset, selfUri))
    def update: F[Dataset] = client.expect(PUT(dataset, selfUri))

    def tables: Stream[F, TableList] = client.pageThrough(GET(selfUri / "tables"))

  extension (table: TableReference)
    def selfUri: Uri =
      DatasetReference(table.projectId, table.datasetId).selfUri / "tables" / table
        .tableId
        .getOrElse("")

    def get: F[Table] = client.expect(GET(selfUri))
    def delete: F[Unit] = client.expect(DELETE(selfUri))

  final class TableData private (table: TableReference):
    def selfUri: Uri = table.selfUri / "data"

    def list(
        maxResults: Option[Int] = None,
        selectedFields: Option[List[String]] = None,
        startIndex: Option[Long] = None
    ): Stream[F, TableDataList] =
      client.pageThrough(
        GET(
          selfUri +?? ("maxResults" -> maxResults) +?? ("selectedFields" -> selectedFields.map(
            _.mkString(","))) +?? ("startIndex" -> startIndex)))

    def listAs[A: TableRowDecoder](
        maxResults: Option[Int] = None,
        selectedFields: Option[List[String]] = None,
        startIndex: Option[Long] = None
    ): Stream[F, A] = for
      dataList <- list(maxResults, selectedFields, startIndex)
      rows <- Stream.fromOption(dataList.rows)
      a <- Stream.evalSeq(rows.traverse(_.as[A]).liftTo[F])
    yield a

    def insertAll(retryFailedRequests: Boolean = false)
        : Pipe[F, TableDataInsertAllRequest, TableDataInsertAllResponse] =
      import GoogleRetryPolicy.*
      _.evalMap(r =>
        client.expect(
          POST(r, selfUri / "insertAll").withAttribute(
            GoogleRetryPolicy.Key,
            {
              case eb: ExponentialBackoff => eb.copy(reckless = retryFailedRequests)
              case d => d
            })))

    def insertAllAs[A: Encoder.AsObject]: Pipe[F, A, TableDataInsertAllResponse] =
      _.map(a => TableDataInsertAllRequestRow(json = a.asJsonObject.some))
        .chunks
        .map(as => TableDataInsertAllRequest(rows = as.toVector.some))
        .through(insertAll())

  extension (row: TableRow)
    def as[A](using d: TableRowDecoder[A]): Either[Throwable, A] =
      d.decode(row)

  def schemaFor[A](using e: TableSchemaEncoder[A]): TableSchema = e.encode

  extension (table: Table)
    def selfUri: Uri = table.tableReference.get.selfUri

    def insert: F[Table] = client.expect(POST(table, selfUri))
    def patch: F[Table] = client.expect(PATCH(table, selfUri))
    def update: F[Table] = client.expect(PUT(table, selfUri))

  extension (job: JobReference)
    def selfUri: Uri =
      ProjectReference(job.projectId).selfUri / "jobs" / job
        .jobId
        .getOrElse("") +?? ("location" -> job.location)
    def mediaUri: Uri =
      ProjectReference(job.projectId).mediaUri / "jobs" / job.jobId.getOrElse("")
    def queryUri: Uri =
      ProjectReference(job.projectId).selfUri / "queries" / job
        .jobId
        .getOrElse("") +?? ("location" -> job.location)

    def get: F[Job] = client.expect(GET(selfUri))
    def cancel: F[Unit] = client.expect(POST(selfUri / "cancel"))

    def getQueryResults(
        maxResults: Option[Long] = None,
        startIndex: Option[Long] = None,
        timeoutMs: Option[FiniteDuration] = None
    ): Stream[F, GetQueryResultsResponse] =
      val req = GET(
        queryUri +?? ("maxResults" -> maxResults) +?? ("startIndex" -> startIndex) +?? ("timeoutMs" -> timeoutMs))
      Stream
        .repeatEval(client.expect[GetQueryResultsResponse](req))
        .takeThrough(!_.jobComplete.contains(true))
        .flatMap { head =>
          val tail =
            if head.jobComplete.contains(true) then
              Stream.fromOption(head.pageToken).flatMap { token =>
                client.pageThrough[GetQueryResultsResponse](
                  req.withUri(req.uri +? ("pageToken" -> token)))
              }
            else Stream.empty
          tail.cons1(head)
        }

    def getQueryResultsAs[A: TableRowDecoder](
        maxResults: Option[Long] = None,
        startIndex: Option[Long] = None,
        timeoutMs: Option[FiniteDuration] = None
    ): Stream[F, A] =
      getQueryResults(maxResults, startIndex, timeoutMs).through(queryResultsAs[A])

  private def queryResultsAs[A: TableRowDecoder](
      in: Stream[F, GetQueryResultsResponse]): Stream[F, A] =
    for
      queryResults <- in
      rows <- Stream
        .fromOption(queryResults.errors.flatMap(NonEmptyVector.fromVector))
        .flatMap(errors => Stream.raiseError(BigQueryException(errors)))
        .ifEmpty(Stream.fromOption(queryResults.rows))
      a <- Stream.evalSeq(rows.traverse(_.as[A]).liftTo[F])
    yield a

  extension (job: Job)
    def selfUri: Uri = job.jobReference.get.selfUri
    def mediaUri: Uri = job.jobReference.get.mediaUri

    def insert: F[Job] = client.expect(POST(job, selfUri))

    def upload: Pipe[F, Byte, Job] =
      if job.configuration.flatMap(_.load).isDefined then
        client.resumableUpload(
          POST(job, mediaUri).withHeaders(
            `X-Upload-Content-Type`(MediaType.application.`octet-stream`)))
      else _ => Stream.raiseError(new IllegalArgumentException("Not an upload job"))

    def uploadAs[A: Encoder.AsObject]: Pipe[F, A, Job] =
      _.map(_.asJson.noSpaces)
        .intersperse("\n")
        .through(text.utf8.encode)
        .through(
          job
            .focus(_.configuration.some.load.some.sourceFormat)
            .replace("NEWLINE_DELIMITED_JSON".some)
            .upload)

    def uploadsAs[A: Encoder.AsObject](rate: FiniteDuration = 1.minute)(
        using Temporal[F]): Pipe[F, A, Job] = in =>
      Stream.eval((Ref.of(false), Queue.synchronous[F, Option[Chunk[A]]]).tupled).flatMap {
        (done, queue) =>
          def go: Stream[F, Job] = Stream
            .fromQueueNoneTerminatedChunk(queue)
            .concurrently(Stream.eval(queue.offer(None).delayBy(rate)))
            .through(uploadAs[A]) ++ Stream.eval(done.get).flatMap { done =>
            if !done then go else Stream.empty
          }
          (in.chunks.map(Some(_)).enqueueUnterminated(queue) ++ Stream
            .eval(done.set(true))
            .drain).merge(go)
      }

  extension (query: QueryRequest)
    def run_(projectId: String): F[QueryResponse] =
      client.expect(POST(query, ProjectReference(projectId.some).selfUri / "queries"))

    def run(projectId: String): Stream[F, Either[QueryResponse, GetQueryResultsResponse]] =
      Stream.eval(run_(projectId)).flatMap { head =>
        val tail =
          if head.jobComplete.contains(true) && head.pageToken.isEmpty then Stream.empty
          else
            for
              jobReference <- Stream.fromOption(head.jobReference)
              results <- jobReference.getQueryResults(
                query.maxResults.map(_ - head.rows.fold(0)(_.size)),
                head.rows.map(_.size),
                query.timeoutMs
              )
            yield Right(results)
        tail.cons1(Left(head)).onFinalize(head.jobReference.fold(F.unit)(_.cancel))
      }

    def runAs[A: TableRowDecoder](projectId: String): Stream[F, A] =
      run(projectId)
        .map {
          case Left(response) =>
            GetQueryResultsResponse(rows = response.rows, errors = response.errors)
          case Right(right) => right
        }
        .through(queryResultsAs)

  given Paginated[ProjectList] with
    extension (pl: ProjectList) def pageToken = pl.nextPageToken

  given Paginated[DatasetList] with
    extension (dl: DatasetList) def pageToken = dl.nextPageToken

  given Paginated[JobList] with
    extension (jl: JobList) def pageToken = jl.nextPageToken

  given Paginated[TableList] with
    extension (tl: TableList) def pageToken = tl.nextPageToken

  given Paginated[TableDataList] with
    extension (tdl: TableDataList) def pageToken = tdl.pageToken

  given Paginated[GetQueryResultsResponse] with
    extension (gqrs: GetQueryResultsResponse) def pageToken = gqrs.pageToken
