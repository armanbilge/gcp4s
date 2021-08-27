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

import cats.MonadThrow
import fs2.Pipe
import fs2.Stream
import org.http4s.Method
import org.http4s.Request
import org.http4s.client.Client
import cats.syntax.all.given
import org.http4s.headers.Location

object ResumableUpload:

  /**
   * Initializes and runs a resumable upload to a media endpoint.
   *
   * @see
   *   [[https://cloud.google.com/storage/docs/resumable-uploads Cloud Storage documentation]]
   * @see
   *   [[https://cloud.google.com/bigquery/docs/reference/api-uploads BigQuery documentation]]
   */
  def apply[F[_]: MonadThrow, A](req: Request[F], chunkSize: Int)(
      using client: Client[F]): Pipe[F, Byte, A] = ???
// in => {

//   val chunkLimitMultiple = 256 * 1024
//   val chunkLimit = (chunkSize & -chunkLimitMultiple).max(chunkLimitMultiple)

//   if req.method != Method.POST then
//     Stream.raiseError(
//       new IllegalArgumentException("Resumable upload must be initiated by POST request"))
//   else if !req.uri.query.exists {
//       case ("uploadType", Some("resumable")) => true
//       case _ => false
//     } then
//     Stream.raiseError(
//       new IllegalArgumentException(
//         "Resumable upload must include query parameter `uploadType=resumable`"))
//   else
//     Stream
//       .eval(
//         client
//           .run(req)
//           .use(
//             _.headers
//               .get[Location]
//               .map(_.uri)
//               .toRight(new RuntimeException("No Location header"))
//               .liftTo))
//       .flatMap { uri => in.chunkLimit(chunkLimit).zipWithScan(0L)(_ + _.size) }
//   ???
// }
