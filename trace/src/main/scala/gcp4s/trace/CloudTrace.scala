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

package gcp4s.trace

import cats.effect.kernel.Clock
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.effect.std.Random
import cats.effect.syntax
import cats.effect.syntax.all.*
import fs2.Stream
import gcp4s.ComputeMetadata
import natchez.EntryPoint
import org.http4s.client.Client
import org.typelevel.log4cats.Logger

object CloudTrace:
  def entryPoint[F[_]: Clock: Random: Logger](
      client: Client[F],
      projectId: String,
      sampler: Sampler[F])(using F: Concurrent[F]): Resource[F, EntryPoint[F]] =
    for
      queue <- Queue.unbounded[F, model.Span].toResource
      traceClient = CloudTraceClient(client, projectId)
      _ <- Stream
        .fromQueueUnterminated(queue)
        .chunks
        .evalMap(c => traceClient.batchWrite(c.toList))
        .attempt
        .evalTap {
          case Left(ex) => Logger[F].error(ex)(ex.getMessage)
          case _ => F.unit
        }
        .compile
        .resource
        .drain
    yield CloudTraceEntryPoint(projectId, queue, sampler)
