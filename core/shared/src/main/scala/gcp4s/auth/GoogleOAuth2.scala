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

import cats.effect.kernel.Temporal
import cats.syntax.all.*
import io.circe.Encoder
import io.circe.generic.semiauto.*
import org.http4s.Method
import org.http4s.Request
import org.http4s.client.Client
import org.http4s.multipart.Multipart
import org.http4s.multipart.Part
import org.http4s.syntax.all.*
import scodec.bits.ByteVector

import scala.concurrent.duration.*

trait GoogleOAuth2[F[_]]:
  def getAccessToken(
      clientEmail: String,
      privateKey: ByteVector,
      scopes: Seq[String]): F[AccessToken]

private[auth] object GoogleOAuth2:
  def apply[F[_]: Temporal: Jwt](client: Client[F]): GoogleOAuth2[F] =
    new GoogleOAuth2:
      val endpoint = uri"https://oauth2.googleapis.com/token"
      def getAccessToken(
          clientEmail: String,
          privateKey: ByteVector,
          scopes: Seq[String]): F[AccessToken] = for
        jwt <- Jwt[F].sign(
          JwtClaimContent(scopes.mkString(" ")),
          endpoint.renderString,
          clientEmail,
          1.hour,
          privateKey
        )
        entity = Multipart[F](
          Vector(
            Part.formData("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer"),
            Part.formData("assertion", jwt)
          )
        )
        request = Request[F](Method.POST, endpoint).withEntity(entity)
        accessToken <- client.expect[AccessToken](request)
      yield accessToken

  final case class JwtClaimContent(scope: String)
  object JwtClaimContent:
    given Encoder[JwtClaimContent] = deriveEncoder
