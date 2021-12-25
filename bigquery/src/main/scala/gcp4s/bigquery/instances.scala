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

import gcp4s.bigquery.model.DatasetList
import gcp4s.bigquery.model.GetQueryResultsResponse
import gcp4s.bigquery.model.JobList
import gcp4s.bigquery.model.ProjectList
import gcp4s.bigquery.model.TableDataList
import gcp4s.bigquery.model.TableList
import gcp4s.bigquery.model.TableRow
import org.http4s.QueryParamEncoder
import org.http4s.QueryParameterValue

import scala.concurrent.duration.FiniteDuration

given QueryParamEncoder[FiniteDuration] =
  QueryParamEncoder.longQueryParamEncoder.contramap(_.toMillis)

given QueryParamEncoder[Map[String, String]] =
  QueryParamEncoder.stringQueryParamEncoder.contramap { filter =>
    filter
      .view
      .map {
        case (key, value) =>
          val colonValue = if value.isEmpty then "" else s":$value"
          s"label.$key$colonValue"
      }
      .mkString(" ")
  }

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

