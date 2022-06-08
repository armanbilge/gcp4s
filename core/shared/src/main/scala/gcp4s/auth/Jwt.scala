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

import cats.MonadThrow
import cats.effect.kernel.Clock
import cats.syntax.all.*
import io.circe.Decoder
import io.circe.Encoder
import io.circe.syntax.*
import scodec.bits.ByteVector

import scala.concurrent.duration.FiniteDuration

sealed trait Jwt[F[_]]:
  def sign[A: Encoder.AsObject](
      payload: A,
      audience: String,
      issuer: String,
      expiresIn: FiniteDuration,
      privateKey: ByteVector
  ): F[String]

object Jwt extends JwtCompanionPlatform:
  inline def apply[F[_]](using jwt: Jwt[F]): jwt.type = jwt

abstract private[auth] class UnsealedJwt[F[_]: Clock](using F: MonadThrow[F]) extends Jwt[F]:
  final case class Header(alg: String = "RS256", typ: String = "JWT")
  object Header:
    given Decoder[Header] = Decoder.forProduct2("alg", "typ")(Header(_, _))
    given Encoder.AsObject[Header] = Encoder.forProduct2("alg", "typ")(h => (h.alg, h.typ))
  val header = ByteVector.encodeAscii(Header().asJson.noSpaces).toOption.get.toBase64UrlNoPad

  final case class Claim(iss: String, aud: String, exp: Long, iat: Long)
  object Claim:
    given Decoder[Claim] = Decoder.forProduct4("iss", "aud", "exp", "iat")(Claim(_, _, _, _))
    given Encoder.AsObject[Claim] =
      Encoder.forProduct4("iss", "aud", "exp", "iat")(c => (c.iss, c.aud, c.exp, c.iat))

  def sign[A: Encoder.AsObject](
      payload: A,
      audience: String,
      issuer: String,
      expiresIn: FiniteDuration,
      privateKey: ByteVector
  ): F[String] =
    for
      iat <- Clock[F].realTime
      claim = Claim(issuer, audience, (iat + expiresIn).toSeconds, iat.toSeconds)
      json = payload.asJsonObject.deepMerge(claim.asJsonObject).asJson
      claim <- ByteVector.encodeAscii(json.noSpaces).liftTo[F].map(_.toBase64UrlNoPad)
      headerClaim <- ByteVector.encodeAscii(s"$header.$claim").liftTo[F]
      signature <- sign(headerClaim, privateKey).map(_.toBase64UrlNoPad)
    yield s"$header.$claim.$signature"

  private[auth] def sign(data: ByteVector, privateKey: ByteVector): F[ByteVector]
