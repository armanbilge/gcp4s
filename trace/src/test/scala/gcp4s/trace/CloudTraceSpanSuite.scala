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

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.effect.std.Random
import cats.syntax.all.*
import munit.CatsEffectSuite
import scodec.bits.ByteVector

class CloudTraceSpanSuite extends CatsEffectSuite {
  test("serialize stack traces once") {
    Random.scalaUtilRandom[IO].flatMap { implicit random =>
      Queue.unbounded[IO, model.Span].flatMap { queue =>
        CloudTraceEntryPoint
          .apply("projectId", queue)
          .root("root")
          .flatMap(_.span("level 1"))
          .flatMap(_.span("level 2"))
          .use(_ => IO.raiseError[Unit](new Exception))
          .attempt
          .void *>
          queue.take.replicateA(3).map {
            case first :: second :: third :: Nil =>
              val stackTraceHashId = first.stackTrace.flatMap(_.stackTraceHashId).get
              assert(first.stackTrace.isDefined)
              assertEquals(
                second.stackTrace.flatMap(_.stackTraceHashId),
                Some(stackTraceHashId))
              assert(second.stackTrace.flatMap(_.stackFrames).isEmpty)
              assertEquals(third.stackTrace.flatMap(_.stackTraceHashId), Some(stackTraceHashId))
              assert(third.stackTrace.flatMap(_.stackFrames).isEmpty)
              ()
            case _ => IO(assert(false))
          }
      }
    }
  }
}
