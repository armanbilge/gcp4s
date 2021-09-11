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

import cats.Functor
import cats.data.EitherT
import cats.effect.kernel.Clock
import cats.effect.kernel.Temporal
import cats.syntax.all.*
import io.circe.Decoder
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf

import scala.concurrent.duration.*

final case class AccessToken(
    token: String,
    expiresAt: FiniteDuration
):
  def expiresSoon[F[_]: Functor: Clock](in: FiniteDuration = 1.minute): F[Boolean] =
    Clock[F].realTime.map { now => expiresAt < now + in }

object AccessToken:
  given [F[_]: Temporal]: EntityDecoder[F, AccessToken] =
    jsonOf[F, AccessTokenResponse].flatMapR {
      case AccessTokenResponse(access_token, token_type, expires_in) =>
        EitherT.liftF(Temporal[F].realTime.map { now =>
          AccessToken(access_token, now + expires_in.seconds)
        })
    }

final private[auth] case class AccessTokenResponse(
    access_token: String,
    token_type: String,
    expires_in: Int)
    derives Decoder
