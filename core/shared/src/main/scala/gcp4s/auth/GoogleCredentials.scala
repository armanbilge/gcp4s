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

import cats.data.OptionT
import cats.effect.kernel.Clock
import cats.effect.kernel.Temporal
import cats.effect.kernel.Deferred
import cats.effect.std.Semaphore
import cats.syntax.all.given
import org.http4s.Credentials
import org.typelevel.ci.*
import scodec.bits.ByteVector

trait GoogleCredentials[F[_]]:
  def get: F[Credentials]

object ServiceAccountCredentials:
  def apply[F[_]: Temporal: GoogleOAuth2](
      clientEmail: String,
      privateKey: ByteVector,
      scopes: Seq[String]): F[GoogleCredentials[F]] =
    OAuth2Credentials(GoogleOAuth2[F].getAccessToken(clientEmail, privateKey, scopes))

object ComputeEngineCredentials:
  def apply[F[_]: Temporal: ComputeMetadata]: F[GoogleCredentials[F]] =
    OAuth2Credentials(ComputeMetadata[F].getAccessToken)

object OAuth2Credentials:
  private[auth] def apply[F[_]](refresh: F[AccessToken])(
      using F: Temporal[F]): F[GoogleCredentials[F]] = for
    token <- F.ref(Option.empty[Deferred[F, Either[Throwable, AccessToken]]])
  yield new GoogleCredentials[F]:
    def get = for AccessToken(token, _) <- getToken
    yield Credentials.Token(ci"bearer", token)

    def getToken: F[AccessToken] = OptionT(token.get)
      .semiflatMap(_.get.rethrow)
      .flatMapF { token =>
        for expired <- token.expiresSoon()
        yield Option.unless(expired)(token)
      }
      .getOrElseF {
        for
          deferred <- F.deferred[Either[Throwable, AccessToken]]
          refreshing <- token.tryUpdate(_ => Some(deferred))
          token <-
            if refreshing then refresh.attempt.flatTap(deferred.complete).rethrow
            else getToken
        yield token
      }
