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
import cats.syntax.all.given
import io.circe.Encoder
import io.circe.generic.semiauto.*
import org.http4s.syntax.all.*
import scodec.bits.ByteVector
import org.http4s.client.Client
import org.http4s.multipart.Multipart
import scala.concurrent.duration.given
import org.http4s.multipart.Part
import org.http4s.Request
import org.http4s.Method

trait GoogleOAuth2[F[_]]:
  def getAccessToken(
      clientEmail: String,
      privateKey: ByteVector,
      scopes: Seq[String]): F[AccessToken]

private[auth] object GoogleOAuth2:
  inline def apply[F[_]](using goa2: GoogleOAuth2[F]): goa2.type = goa2

  given [F[_]: Temporal: Jwt](using client: Client[F]): GoogleOAuth2[F] with
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
      given encoder: Encoder[JwtClaimContent] = deriveEncoder
