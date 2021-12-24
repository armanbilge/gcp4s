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

import cats.effect.kernel.Concurrent
import cats.syntax.all.*
import gcp4s.trace.model.BatchWriteSpansRequest
import io.circe.Encoder
import org.http4s.EntityEncoder
import org.http4s.Method.POST
import org.http4s.circe.CirceEntityDecoder
import org.http4s.circe.CirceInstances
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.syntax.all.*

final private class CloudTraceClient[F[_]: Concurrent](client: Client[F], projectId: String)
    extends Http4sClientDsl[F],
      CirceInstances,
      CirceEntityDecoder:
  private val endpoint = uri"https://cloudtrace.googleapis.com/v2/projects"

  override protected val defaultPrinter = super.defaultPrinter.copy(dropNullValues = true)
  private given [F[_], A: Encoder]: EntityEncoder[F, A] =
    jsonEncoderOf[F, A]

  def batchWrite(spans: List[model.Span]): F[Unit] =
    client.expect(
      POST(BatchWriteSpansRequest(spans.some), endpoint / projectId / "traces:batchWrite"))
