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

import cats.MonadThrow
import cats.effect.kernel.Clock
import cats.syntax.all.*
import scodec.bits.ByteVector

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Uint8Array

abstract private[auth] class JwtCompanionPlatform:
  given [F[_]: Clock](using F: MonadThrow[F]): Jwt[F] =
    new UnsealedJwt[F]:
      def sign(data: ByteVector, privateKey: ByteVector): F[ByteVector] =
        F.catchNonFatal {
          val key = crypto.createPrivateKey(new {
            val key = privateKey.toUint8Array
            val format = "der"
            val `type` = "pkcs8"
          })
          ByteVector.view(crypto.sign("SHA256", data.toUint8Array, key))
        }

@JSImport("crypto", JSImport.Default)
@js.native
private[auth] object crypto extends js.Object:
  def sign(algorithm: "SHA256", data: Uint8Array, key: KeyObject): Uint8Array = js.native
  def createPrivateKey(key: Key): KeyObject = js.native

private[auth] trait KeyObject extends js.Object

private[auth] trait Key extends js.Object:
  val key: Uint8Array
  val format: "der"
  val `type`: "pkcs8"
