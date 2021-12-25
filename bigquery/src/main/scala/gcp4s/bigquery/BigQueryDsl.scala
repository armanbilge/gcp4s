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

import cats.data.NonEmptyList
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
import gcp4s.json.given
import io.circe.Encoder
import io.circe.syntax.*
import monocle.syntax.all.focus
import org.http4s.MediaType
import org.http4s.Method.*
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.syntax.all.*

import scala.concurrent.duration.*

final class BigQueryClient[F[_]](client: Client[F])(using F: Temporal[F])
    extends Http4sClientDsl[F]:

  val endpoint = uri"https://bigquery.googleapis.com/bigquery/v2/"
  val mediaEndpoint = uri"https://bigquery.googleapis.com/upload/bigquery/v2/"

  object projects:
    def uri(project: ProjectReference): Uri =
      endpoint / "projects" / project.projectId.getOrElse("")

    def mediaUri(project: ProjectReference): Uri =
      mediaEndpoint / "projects" / project.projectId.getOrElse("")

    def list: Stream[F, ProjectList] =
      client.pageThrough(GET(endpoint / "projects"))

  object datasets:
    def uri(dataset: DatasetReference): Uri =
      val project = ProjectReference(dataset.projectId)
      projects.uri(project) / "datasets" / dataset.datasetId.getOrElse("")

    def list(
        project: ProjectReference,
        all: Option[Boolean] = None,
        filter: Option[Map[String, String]] = None): Stream[F, DatasetListDataset] =
      for
        list <- client.pageThrough[DatasetList](
          GET(projects.uri(project) / "datasets" +?? ("all" -> all) +?? ("filter" -> filter)))
        datasets <- Stream.fromOption(list.datasets)
        dataset <- Stream.emits(datasets)
      yield dataset

    def get(dataset: DatasetReference): F[Dataset] = client.expect(GET(uri(dataset)))

    def delete(dataset: DatasetReference, deleteContents: Option[Boolean] = None): F[Unit] =
      client.expect(DELETE(uri(dataset)))

    def insert(dataset: Dataset): F[Dataset] =
      client.expect(POST(dataset, uri(dataset.datasetReference.get.copy(datasetId = None))))

    def patch(dataset: Dataset): F[Dataset] =
      client.expect(PATCH(dataset, uri(dataset.datasetReference.get)))

    def update(dataset: Dataset): F[Dataset] =
      client.expect(PUT(dataset, uri(dataset.datasetReference.get)))

  object tables:
    def uri(table: TableReference): Uri =
      val dataset = DatasetReference(projectId = table.projectId, datasetId = table.datasetId)
      datasets.uri(dataset) / "tables" / table.tableId.getOrElse("")

    def list(dataset: DatasetReference): Stream[F, TableListTable] =
      for
        list <- client.pageThrough[TableList](GET(datasets.uri(dataset) / "tables"))
        tables <- Stream.fromOption(list.tables)
        table <- Stream.emits(tables)
      yield table

    def get(table: TableReference): F[Table] = client.expect(GET(uri(table)))

    def delete(table: TableReference): F[Unit] = client.expect(DELETE(uri(table)))

    def insert(table: Table): F[Table] =
      client.expect(POST(table, uri(table.tableReference.get.copy(tableId = None))))

    def patch(table: Table): F[Table] =
      client.expect(PATCH(table, uri(table.tableReference.get)))

    def update(table: Table): F[Table] =
      client.expect(PUT(table, uri(table.tableReference.get)))

  object tableData:
    def uri(table: TableReference): Uri = tables.uri(table) / "data"

    def list(
        table: TableReference,
        maxResults: Option[Int] = None,
        selectedFields: Option[List[String]] = None,
        startIndex: Option[Long] = None
    ): Stream[F, TableDataList] =
      client.pageThrough(
        GET(
          uri(table) +?? ("maxResults" -> maxResults) +?? ("selectedFields" -> selectedFields
            .map(_.mkString(","))) +?? ("startIndex" -> startIndex)))

    def listAs[A: TableRowDecoder](
        table: TableReference,
        maxResults: Option[Int] = None,
        selectedFields: Option[List[String]] = None,
        startIndex: Option[Long] = None
    ): Stream[F, A] = for
      dataList <- list(table, maxResults, selectedFields, startIndex)
      rows <- Stream.fromOption(dataList.rows)
      a <- Stream.evalSeq(rows.traverse(_.as[A]).liftTo[F])
    yield a

    def insertAll(table: TableReference, retryFailedRequests: Boolean = false)
        : Pipe[F, TableDataInsertAllRequest, TableDataInsertAllResponse] =
      import GoogleRetryPolicy.*
      _.evalMap(r =>
        client.expect(
          POST(r, uri(table) / "insertAll").withAttribute(
            GoogleRetryPolicy.Key,
            {
              case eb: ExponentialBackoff => eb.copy(reckless = retryFailedRequests)
              case d => d
            })))

    def insertAllAs[A: Encoder.AsObject](
        table: TableReference): Pipe[F, A, TableDataInsertAllResponse] =
      _.map(a => TableDataInsertAllRequestRow(json = a.asJsonObject.some))
        .chunks
        .map(as => TableDataInsertAllRequest(rows = as.toList.some))
        .through(insertAll(table))

  object jobs:
    def uri(job: JobReference): Uri =
      val project = ProjectReference(projectId = job.projectId)
      projects.uri(project) / "jobs" / job.jobId.getOrElse("") +?? ("location" -> job.location)

    def mediaUri(job: JobReference): Uri =
      val project = ProjectReference(projectId = job.projectId)
      projects.mediaUri(project) / "jobs" / job.jobId.getOrElse("")

    def queryUri(job: JobReference): Uri =
      val project = ProjectReference(projectId = job.projectId)
      projects
        .uri(project) / "queries" / job.jobId.getOrElse("") +?? ("location" -> job.location)

    def list(
        project: ProjectReference,
        allUsers: Option[Boolean] = None,
        maxCreationTime: Option[FiniteDuration] = None,
        minCreationTime: Option[FiniteDuration] = None,
        parentJobId: Option[JobReference] = None,
        projection: Option["full" | "minimal"] = None,
        stateFilter: Option[List["done" | "pending" | "running"]] = None
    ): Stream[F, JobListJob] =
      for
        list <- client.pageThrough[JobList](
          GET(
            projects.uri(
              project) / "jobs" +?? ("allUsers" -> allUsers) +?? ("maxCreationTime" -> maxCreationTime) +?? ("minCreationTime" -> minCreationTime) +?? ("parentJobId" -> parentJobId
              .flatMap(_.jobId)) +?? ("projection" -> projection
              .widen[String]) ++? ("stateFilter" -> stateFilter
              .widen[Seq[String]]
              .getOrElse(Seq.empty))
          )
        )
        jobs <- Stream.fromOption(list.jobs)
        job <- Stream.emits(jobs)
      yield job

    def get(job: JobReference): F[Job] = client.expect(GET(uri(job)))

    def cancel(job: JobReference): F[JobCancelResponse] =
      client.expect(POST(uri(job) / "cancel"))

    def getQueryResults(
        job: JobReference,
        maxResults: Option[Long] = None,
        startIndex: Option[Long] = None,
        timeoutMs: Option[FiniteDuration] = None
    ): Stream[F, GetQueryResultsResponse] =
      val req = GET(queryUri(
        job) +?? ("maxResults" -> maxResults) +?? ("startIndex" -> startIndex) +?? ("timeoutMs" -> timeoutMs))
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
        job: JobReference,
        maxResults: Option[Long] = None,
        startIndex: Option[Long] = None,
        timeoutMs: Option[FiniteDuration] = None
    ): Stream[F, A] =
      getQueryResults(job, maxResults, startIndex, timeoutMs).through(queryResultsAs[A])

    private def queryResultsAs[A: TableRowDecoder](
        in: Stream[F, GetQueryResultsResponse]): Stream[F, A] =
      for
        queryResults <- in
        rows <- Stream
          .fromOption(queryResults.errors.flatMap(NonEmptyList.fromList))
          .flatMap(errors => Stream.raiseError(BigQueryException(errors)))
          .ifEmpty(Stream.fromOption(queryResults.rows))
        a <- Stream.evalSeq(rows.traverse(_.as[A]).liftTo[F])
      yield a

    def insert(job: Job): F[Job] = client.expect(POST(job, uri(job.jobReference.get)))

    def upload(job: Job): Pipe[F, Byte, Job] =
      if job.configuration.flatMap(_.load).isDefined then
        client.resumableUpload[Job](
          POST(job, mediaUri(job.jobReference.get))
            .withHeaders(`X-Upload-Content-Type`(MediaType.application.`octet-stream`)))
      else _ => Stream.raiseError(new IllegalArgumentException("Not an upload job"))

    def uploadAs[A: Encoder.AsObject](job: Job): Pipe[F, A, Job] =
      _.map(_.asJson.noSpaces)
        .intersperse("\n")
        .through(text.utf8.encode)
        .through(
          upload(
            job
              .focus(_.configuration.some.load.some.sourceFormat)
              .replace("NEWLINE_DELIMITED_JSON".some)
          ))

    def uploadsAs[A: Encoder.AsObject](
        job: Job,
        rate: FiniteDuration = 1.minute): Pipe[F, A, Job] = in =>
      Stream.eval((Ref.of(false), Queue.synchronous[F, Option[Chunk[A]]]).tupled).flatMap {
        (done, queue) =>
          def go: Stream[F, Job] = Stream
            .fromQueueNoneTerminatedChunk(queue)
            .concurrently(Stream.eval(queue.offer(None).delayBy(rate)))
            .through(uploadAs[A](job)) ++ Stream.eval(done.get.product(queue.size)).flatMap {
            (done, size) => if !done | size > 0 then go else Stream.empty
          }
          (in.chunks.enqueueNoneTerminated(queue) ++ Stream.eval(done.set(true)).drain)
            .merge(go)
      }

    def query_(query: QueryRequest, project: ProjectReference): F[QueryResponse] =
        client.expect(POST(query, projects.uri(project) / "queries"))

    def run(query: QueryRequest, project: ProjectReference): Stream[F, Either[QueryResponse, GetQueryResultsResponse]] =
      Stream.eval(query_(query, project)).flatMap { head =>
        val tail =
          if head.jobComplete.contains(true) && head.pageToken.isEmpty then Stream.empty
          else
            for
              jobReference <- Stream.fromOption(head.jobReference)
              results <- getQueryResults(
                jobReference,
                query.maxResults.map(_ - head.rows.fold(0)(_.size)),
                head.rows.map(_.size),
                query.timeoutMs
              )
            yield Right(results)
        tail.cons1(Left(head)).onFinalize(head.jobReference.fold(F.unit)(cancel(_).void))
      }

    def runAs[A: TableRowDecoder](query: QueryRequest, project: ProjectReference): Stream[F, A] =
      run(query, project)
        .map {
          case Left(response) =>
            GetQueryResultsResponse(rows = response.rows, errors = response.errors)
          case Right(right) => right
        }
        .through(queryResultsAs)

// object BigQueryDsl:
//   def apply[F[_]: Concurrent](client: Client[F]) = new BigQueryDsl(client) {}

// trait BigQueryDsl[F[_]](client: Client[F])(using F: Concurrent[F]) extends Http4sClientDsl[F]:

//   val endpoint = uri"https://bigquery.googleapis.com/bigquery/v2/"
//   val mediaEndpoint = uri"https://bigquery.googleapis.com/upload/bigquery/v2/"

//   def projects: Stream[F, ProjectList] =
//     client.pageThrough(GET(endpoint / "projects"))

//   extension (project: ProjectReference)
//     def selfUri: Uri = endpoint / "projects" / project.projectId.getOrElse("")
//     def mediaUri: Uri = mediaEndpoint / "projects" / project.projectId.getOrElse("")

//     def datasets(
//         all: Option[Boolean] = None,
//         filter: Option[Map[String, String]] = None): Stream[F, DatasetListDataset] =
//       for
//         list <- client.pageThrough[DatasetList](
//           GET(selfUri / "datasets" +?? ("all" -> all) +?? ("filter" -> filter)))
//         datasets <- Stream.fromOption(list.datasets)
//         dataset <- Stream.emits(datasets)
//       yield dataset

//     def jobs(
//         allUsers: Option[Boolean] = None,
//         maxCreationTime: Option[FiniteDuration] = None,
//         minCreationTime: Option[FiniteDuration] = None,
//         parentJobId: Option[JobReference] = None,
//         projection: Option["full" | "minimal"] = None,
//         stateFilter: Option[List["done" | "pending" | "running"]] = None
//     ): Stream[F, JobListJob] =
//       for
//         list <- client.pageThrough[JobList](
//           GET(
//             selfUri / "jobs" +?? ("allUsers" -> allUsers) +?? ("maxCreationTime" -> maxCreationTime) +?? ("minCreationTime" -> minCreationTime) +?? ("parentJobId" -> parentJobId
//               .flatMap(_.jobId)) +?? ("projection" -> projection
//               .widen[String]) ++? ("stateFilter" -> stateFilter
//               .widen[Seq[String]]
//               .getOrElse(Seq.empty))
//           )
//         )
//         jobs <- Stream.fromOption(list.jobs)
//         job <- Stream.emits(jobs)
//       yield job

//   extension (dataset: DatasetReference)
//     def selfUri: Uri =
//       ProjectReference(dataset.projectId).selfUri / "datasets" / dataset.datasetId.getOrElse("")

//     def get: F[Dataset] = client.expect(GET(selfUri))
//     def delete(deleteContents: Option[Boolean] = None): F[Unit] = client.expect(DELETE(selfUri))

//     def tables: Stream[F, TableListTable] =
//       for
//         list <- client.pageThrough[TableList](GET(selfUri / "tables"))
//         tables <- Stream.fromOption(list.tables)
//         table <- Stream.emits(tables)
//       yield table

//   extension (dataset: Dataset)
//     def selfUri: Uri = dataset.datasetReference.get.selfUri
//     def insertUri = dataset.datasetReference.get.copy(datasetId = None).selfUri

//     def insert: F[Dataset] = client.expect(POST(dataset, insertUri))
//     def patch: F[Dataset] = client.expect(PATCH(dataset, selfUri))
//     def update: F[Dataset] = client.expect(PUT(dataset, selfUri))

//   extension (table: TableReference)
//     def selfUri: Uri =
//       DatasetReference(
//         projectId = table.projectId,
//         datasetId = table.datasetId).selfUri / "tables" / table.tableId.getOrElse("")

//     def get: F[Table] = client.expect(GET(selfUri))
//     def delete: F[Unit] = client.expect(DELETE(selfUri))
//     def data: TableData = new TableData(table)

//   final class TableData private[bigquery] (table: TableReference):
//     def selfUri: Uri = table.selfUri / "data"

//     def list(
//         maxResults: Option[Int] = None,
//         selectedFields: Option[List[String]] = None,
//         startIndex: Option[Long] = None
//     ): Stream[F, TableDataList] =
//       client.pageThrough(
//         GET(
//           selfUri +?? ("maxResults" -> maxResults) +?? ("selectedFields" -> selectedFields.map(
//             _.mkString(","))) +?? ("startIndex" -> startIndex)))

//     def listAs[A: TableRowDecoder](
//         maxResults: Option[Int] = None,
//         selectedFields: Option[List[String]] = None,
//         startIndex: Option[Long] = None
//     ): Stream[F, A] = for
//       dataList <- list(maxResults, selectedFields, startIndex)
//       rows <- Stream.fromOption(dataList.rows)
//       a <- Stream.evalSeq(rows.traverse(_.as[A]).liftTo[F])
//     yield a

//     def insertAll(retryFailedRequests: Boolean = false)
//         : Pipe[F, TableDataInsertAllRequest, TableDataInsertAllResponse] =
//       import GoogleRetryPolicy.*
//       _.evalMap(r =>
//         client.expect(
//           POST(r, selfUri / "insertAll").withAttribute(
//             GoogleRetryPolicy.Key,
//             {
//               case eb: ExponentialBackoff => eb.copy(reckless = retryFailedRequests)
//               case d => d
//             })))

//     def insertAllAs[A: Encoder.AsObject]: Pipe[F, A, TableDataInsertAllResponse] =
//       _.map(a => TableDataInsertAllRequestRow(json = a.asJsonObject.some))
//         .chunks
//         .map(as => TableDataInsertAllRequest(rows = as.toList.some))
//         .through(insertAll())

//   extension (row: TableRow)
//     def as[A](using d: TableRowDecoder[A]): Either[Throwable, A] =
//       d.decode(row)

//   def schemaFor[A](using e: TableSchemaEncoder[A]): TableSchema = e.encode

//   extension (table: Table)
//     def selfUri: Uri = table.tableReference.get.selfUri
//     def insertUri = table.tableReference.get.copy(tableId = None).selfUri

//     def insert: F[Table] = client.expect(POST(table, insertUri))
//     def patch: F[Table] = client.expect(PATCH(table, selfUri))
//     def update: F[Table] = client.expect(PUT(table, selfUri))

//   extension (job: JobReference)
//     def selfUri: Uri =
//       ProjectReference(job.projectId).selfUri / "jobs" / job
//         .jobId
//         .getOrElse("") +?? ("location" -> job.location)
//     def mediaUri: Uri =
//       ProjectReference(job.projectId).mediaUri / "jobs" / job.jobId.getOrElse("")
//     def queryUri: Uri =
//       ProjectReference(job.projectId).selfUri / "queries" / job
//         .jobId
//         .getOrElse("") +?? ("location" -> job.location)

//     def get: F[Job] = client.expect(GET(selfUri))
//     def cancel: F[JobCancelResponse] = client.expect(POST(selfUri / "cancel"))

//     def getQueryResults(
//         maxResults: Option[Long] = None,
//         startIndex: Option[Long] = None,
//         timeoutMs: Option[FiniteDuration] = None
//     ): Stream[F, GetQueryResultsResponse] =
//       val req = GET(
//         queryUri +?? ("maxResults" -> maxResults) +?? ("startIndex" -> startIndex) +?? ("timeoutMs" -> timeoutMs))
//       Stream
//         .repeatEval(client.expect[GetQueryResultsResponse](req))
//         .takeThrough(!_.jobComplete.contains(true))
//         .flatMap { head =>
//           val tail =
//             if head.jobComplete.contains(true) then
//               Stream.fromOption(head.pageToken).flatMap { token =>
//                 client.pageThrough[GetQueryResultsResponse](
//                   req.withUri(req.uri +? ("pageToken" -> token)))
//               }
//             else Stream.empty
//           tail.cons1(head)
//         }

//     def getQueryResultsAs[A: TableRowDecoder](
//         maxResults: Option[Long] = None,
//         startIndex: Option[Long] = None,
//         timeoutMs: Option[FiniteDuration] = None
//     ): Stream[F, A] =
//       getQueryResults(maxResults, startIndex, timeoutMs).through(queryResultsAs[A])

//   private def queryResultsAs[A: TableRowDecoder](
//       in: Stream[F, GetQueryResultsResponse]): Stream[F, A] =
//     for
//       queryResults <- in
//       rows <- Stream
//         .fromOption(queryResults.errors.flatMap(NonEmptyList.fromList))
//         .flatMap(errors => Stream.raiseError(BigQueryException(errors)))
//         .ifEmpty(Stream.fromOption(queryResults.rows))
//       a <- Stream.evalSeq(rows.traverse(_.as[A]).liftTo[F])
//     yield a

//   extension (job: Job)
//     def selfUri: Uri = job.jobReference.get.selfUri
//     def mediaUri: Uri = job.jobReference.get.mediaUri

//     def insert: F[Job] = client.expect(POST(job, selfUri))

//     def upload: Pipe[F, Byte, Job] =
//       if job.configuration.flatMap(_.load).isDefined then
//         client.resumableUpload[Job](
//           POST(job, mediaUri).withHeaders(
//             `X-Upload-Content-Type`(MediaType.application.`octet-stream`)))
//       else _ => Stream.raiseError(new IllegalArgumentException("Not an upload job"))

//     def uploadAs[A: Encoder.AsObject]: Pipe[F, A, Job] =
//       _.map(_.asJson.noSpaces)
//         .intersperse("\n")
//         .through(text.utf8.encode)
//         .through(
//           job
//             .focus(_.configuration.some.load.some.sourceFormat)
//             .replace("NEWLINE_DELIMITED_JSON".some)
//             .upload)

//     def uploadsAs[A: Encoder.AsObject](rate: FiniteDuration = 1.minute)(
//         using Temporal[F]): Pipe[F, A, Job] = in =>
//       Stream.eval((Ref.of(false), Queue.synchronous[F, Option[Chunk[A]]]).tupled).flatMap {
//         (done, queue) =>
//           def go: Stream[F, Job] = Stream
//             .fromQueueNoneTerminatedChunk(queue)
//             .concurrently(Stream.eval(queue.offer(None).delayBy(rate)))
//             .through(uploadAs[A]) ++ Stream.eval(done.get.product(queue.size)).flatMap {
//             (done, size) => if !done | size > 0 then go else Stream.empty
//           }
//           (in.chunks.enqueueNoneTerminated(queue) ++ Stream.eval(done.set(true)).drain)
//             .merge(go)
//       }

//   extension (query: QueryRequest)
//     def run_(projectId: String): F[QueryResponse] =
//       client.expect(POST(query, ProjectReference(projectId.some).selfUri / "queries"))

//     def run(projectId: String): Stream[F, Either[QueryResponse, GetQueryResultsResponse]] =
//       Stream.eval(run_(projectId)).flatMap { head =>
//         val tail =
//           if head.jobComplete.contains(true) && head.pageToken.isEmpty then Stream.empty
//           else
//             for
//               jobReference <- Stream.fromOption(head.jobReference)
//               results <- jobReference.getQueryResults(
//                 query.maxResults.map(_ - head.rows.fold(0)(_.size)),
//                 head.rows.map(_.size),
//                 query.timeoutMs
//               )
//             yield Right(results)
//         tail.cons1(Left(head)).onFinalize(head.jobReference.fold(F.unit)(_.cancel.void))
//       }

//     def runAs[A: TableRowDecoder](projectId: String): Stream[F, A] =
//       run(projectId)
//         .map {
//           case Left(response) =>
//             GetQueryResultsResponse(rows = response.rows, errors = response.errors)
//           case Right(right) => right
//         }
//         .through(queryResultsAs)
