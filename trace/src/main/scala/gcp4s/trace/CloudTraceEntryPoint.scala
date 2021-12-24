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

import cats.data.OptionT
import cats.effect.kernel.Clock
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Resource
import cats.effect.std.QueueSink
import cats.effect.std.Random
import cats.syntax.all.*
import cats.effect.syntax.all.*
import natchez.EntryPoint
import natchez.Kernel
import natchez.Span
import scodec.bits.ByteVector

final private class CloudTraceEntryPoint[F[_]: Clock: Random](
    projectId: String,
    sink: QueueSink[F, model.Span],
    sampler: Sampler[F])(using F: Concurrent[F])
    extends EntryPoint[F]:

  def root(name: String): Resource[F, Span[F]] =
    for
      traceId <- Random[F].nextBytes(16).map(ByteVector(_)).toResource
      sample <- sampler.sample.toResource
      flags = Some[Byte](if sample then 1 else 0)
      span <- CloudTraceSpan(sink, name, projectId, traceId, flags, _ => F.unit)
    yield span

  def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
    tryContinue(name, kernel).getOrElseF(
      new NoSuchElementException(`X-Cloud-Trace-Context`.name).raiseError)

  def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
    tryContinue(name, kernel).getOrElseF(root(name))

  private def tryContinue(name: String, kernel: Kernel): OptionT[Resource[F, _], Span[F]] =
    val header = kernel
      .toHeaders
      .get(`X-Cloud-Trace-Context`.name)
      .flatMap(`X-Cloud-Trace-Context`.parse(_))
    OptionT.fromOption[Resource[F, _]](header).semiflatMap {
      case `X-Cloud-Trace-Context`(traceId, spanId, force) =>
        CloudTraceSpan(sink, name, projectId, traceId, force, _ => F.unit, spanId, false)
    }
