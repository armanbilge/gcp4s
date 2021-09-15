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
package auth

import io.circe.Encoder
import scala.concurrent.duration.FiniteDuration
import scodec.bits.ByteVector

trait Jwt[F[_]]:
  def sign[A: Encoder.AsObject](
      payload: A,
      audience: String,
      issuer: String,
      expiresIn: FiniteDuration,
      privateKey: ByteVector
  ): F[String]

object Jwt extends JwtCompanionPlatform:
  inline def apply[F[_]](using jwt: Jwt[F]): jwt.type = jwt
