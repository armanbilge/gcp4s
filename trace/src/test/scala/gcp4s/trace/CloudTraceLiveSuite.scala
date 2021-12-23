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
package trace

import cats.effect.IO
import cats.effect.kernel.Deferred
import cats.effect.std.Random
import cats.effect.syntax.all.*
import natchez.Trace
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

class CloudTraceLiveSuite extends Gcp4sLiveSuite:

  test("Send live traces") {
    val span = for
      client <- googleClient
      projectId <- googleCredentials.map(_.projectId)
      given Random[IO] <- Random.scalaUtilRandom[IO]
      error <- IO.deferred[Throwable]
      given Logger[IO] = errorLogger(error)
      _ <- CloudTrace
        .entryPoint[IO](client, projectId, Sampler.always[IO])
        .flatMap(_.root(getClass.getSimpleName))
        .evalMap(Trace.ioTrace(_))
        .use { trace =>
          trace.span("simple")(trace.put("hello" -> "world")) *>
            trace.span("error")(IO.raiseError(new Exception)).attempt *>
            trace.span("canceled")(IO.canceled).start.flatMap(_.join)
        }
      _ <- error.tryGet.flatMap(e => IO(assertEquals(e, None)))
    yield ()
  }

  def errorLogger(deferred: Deferred[IO, Throwable]): Logger[IO] = new:
    def debug(t: Throwable)(message: => String) = IO.unit
    def error(t: Throwable)(message: => String) = deferred.complete(t).void
    def info(t: Throwable)(message: => String) = IO.unit
    def trace(t: Throwable)(message: => String) = IO.unit
    def warn(t: Throwable)(message: => String) = IO.unit
    def debug(message: => String) = IO.unit
    def error(message: => String) = IO.unit
    def info(message: => String) = IO.unit
    def trace(message: => String) = IO.unit
    def warn(message: => String) = IO.unit
