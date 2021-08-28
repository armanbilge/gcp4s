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
import cats.syntax.all.given
import io.circe.Encoder
import io.circe.syntax.given
import pdi.jwt.JwtClaim
import pdi.jwt.JwtCirce
import pdi.jwt.JwtAlgorithm.RS256
import scala.concurrent.duration.*
import scodec.bits.ByteVector
import cats.ApplicativeThrow
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec

abstract private[auth] class JwtCompanionPlatform:
  given [F[_]: Clock](using F: MonadThrow[F]): Jwt[F] with
    def sign[A: Encoder](
        payload: A,
        audience: String,
        issuer: String,
        expiresIn: FiniteDuration,
        privateKey: ByteVector
    ): F[String] =
      val claim = JwtClaim(
        content = payload.asJson.noSpaces,
        audience = Some(Set(audience)),
        issuer = Some(issuer))

      val keyFactory = KeyFactory.getInstance("RSA")

      for
        key <- F.catchNonFatal {
          val spec = new PKCS8EncodedKeySpec(privateKey.toArray)
          keyFactory.generatePrivate(spec)
        }
        now <- Clock[F].realTime
        issued = claim.expiresAt((now + 1.hour).toSeconds).issuedAt(now.toSeconds)
      yield JwtCirce.encode(issued, key, RS256)
