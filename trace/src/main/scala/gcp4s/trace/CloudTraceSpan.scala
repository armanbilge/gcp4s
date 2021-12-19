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

import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Resource.ExitCase
import cats.effect.kernel.Temporal
import cats.effect.std.Random
import cats.effect.syntax.all.*
import cats.syntax.all.*
import gcp4s.trace.model.Link
import gcp4s.trace.model.Links
import natchez.Kernel
import natchez.Span
import natchez.TraceValue
import scodec.bits.ByteVector

import java.net.URI
import java.time.Instant
import scala.concurrent.duration.FiniteDuration

final private class CloudTraceSpan[F[_]: Temporal](
    val projectId: String,
    val _traceId: ByteVector,
    val _spanId: Long,
    val childCount: Ref[F, Int],
    val attributes: Ref[F, List[(String, TraceValue)]],
    val startTime: FiniteDuration,
    val sink: model.Span => F[Unit]
)(using F: Random[F])
    extends Span[F]:

  def resourceName: String =
    s"projects/$projectId/traces/${_traceId.toHex}/spans/${ByteVector.fromLong(_spanId).toHex}"

  def kernel: F[Kernel] =
    val header = `X-Cloud-Trace-Context`(_traceId, _spanId).toHeader
    Kernel(Map(header)).pure

  def put(fields: Seq[(String, TraceValue)]): F[Unit] =
    attributes.update(_.prependedAll(fields))

  def span(name: String): Resource[F, natchez.Span[F]] = Resource.uncancelable { poll =>
    childCount.update(_ + 1).toResource *> poll(
      CloudTraceSpan.make[F](sink)(name, projectId, _traceId, _spanId))
  }

  def spanId: F[Option[String]] = ByteVector.fromLong(_spanId).toHex.some.pure

  def traceId: F[Option[String]] = _traceId.toHex.some.pure

  def traceUri: F[Option[URI]] = URI(s"projects/$projectId/traces/${_traceId.toHex}").some.pure

private object CloudTraceSpan:
  def make[F[_]: Random](sink: model.Span => F[Unit])(
      name: String,
      projectId: String,
      traceId: ByteVector,
      parentSpanId: Long = 0,
      sameProcess: Boolean = true)(using F: Temporal[F]): Resource[F, Span[F]] =

    val nextSpanId = Random[F].nextLong.iterateUntil(_ != 0L)

    (nextSpanId, F.ref(0), F.ref(List.empty[(String, TraceValue)]), F.realTime).mapN

    Resource.makeCase {
      for
        spanId <- Random[F].nextLong.iterateUntil(_ != 0L)
        childCount <- F.ref(0)
        attributes <- F.ref(List.empty[(String, TraceValue)])
        now <- F.realTime
      yield CloudTraceSpan(
        projectId,
        traceId,
        spanId,
        childCount,
        attributes,
        now,
        sink
      )
    } { (span, exit) =>
      (F.realTime, span.childCount.get, span.attributes.get).mapN {
        (endTime, childCount, attributes) =>

          val stackTrace = exit match
            case ExitCase.Errored(e) => Some(encodeStackTrace(e))
            case _ => None

          val links = Option.when(parentSpanId != 0) {
            Links(
              link = List(
                Link(
                  traceId = traceId.toHex.some,
                  `type` = "PARENT_LINKED_SPAN".some,
                  spanId = ByteVector.fromLong(parentSpanId).toHex.some
                )).some
            )
          }

          val serialized = model.Span(
            displayName = encodeTruncatableString(name, 128).some,
            name = span.resourceName.some,
            startTime = Instant.ofEpochMilli(span.startTime.toMillis).toString.some,
            stackTrace = stackTrace,
            attributes = ???,
            endTime = Instant.ofEpochMilli(endTime.toMillis).toString.some,
            links = links,
            childSpanCount = childCount.some,
            sameProcessAsParentSpan = sameProcess.some
          )
          sink(serialized)
      }.flatten
    }
