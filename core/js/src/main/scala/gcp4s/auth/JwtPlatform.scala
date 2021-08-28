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

package gcp4s.auth

import cats.effect.kernel.Async
import fs2.Chunk
import io.circe.Encoder
import scala.concurrent.duration.FiniteDuration
import scodec.bits.ByteVector
import cats.syntax.all.given
import io.circe.scalajs.given
import scala.concurrent.duration.given
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Uint8Array

abstract private[auth] class JwtCompanionPlatform:
  given [F[_]](using F: Async[F]): Jwt[F] with
    def sign[A: Encoder](
        payload: A,
        audience: String,
        issuer: String,
        expiresIn: FiniteDuration,
        privateKey: ByteVector
    ): F[String] =
      F.async_ { cb =>
        jsonwebtoken.sign(
          payload.asJsAny,
          Chunk.byteVector(privateKey).toUint8Array,
          SignOptions("RS256", audience, issuer, expiresIn.toSeconds.toDouble),
          (err, signed) => cb(signed.toRight(js.JavaScriptException(err)))
        )

      }

private[auth] object jsonwebtoken:

  @js.native
  @JSImport("jsonwebtoken", "sign")
  def sign(
      payload: js.Any,
      secretOrPrivateKey: Uint8Array,
      options: SignOptions,
      callback: SignCallback): Unit = js.native

private[auth] type SignCallback = js.Function2[js.Error, js.UndefOr[String], Unit]

@js.native
private[auth] trait SignOptions extends js.Any
object SignOptions:
  def apply(
      algorithm: "RS256",
      audience: String,
      issuer: String,
      expiresIn: Double): SignOptions =
    js.Dynamic
      .literal(
        algorithm = algorithm,
        audience = audience,
        issuer = issuer
      )
      .asInstanceOf[SignOptions]
