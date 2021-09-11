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

import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.kernel.Clock
import cats.effect.kernel.Deferred
import cats.effect.kernel.MonadCancelThrow
import cats.effect.kernel.Temporal
import cats.effect.std.Semaphore
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.CompositeFailure
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.text
import io.circe.Decoder
import io.circe.parser
import org.http4s.Credentials
import org.http4s.client.Client
import org.http4s.client.Middleware
import org.http4s.headers.Authorization
import org.typelevel.ci.*
import scodec.bits.ByteVector

trait GoogleCredentials[F[_]]:
  def projectId: String
  def get: F[Credentials]

  def middleware(using MonadCancelThrow[F]): Middleware[F] =
    client =>
      Client { req =>
        for
          creds <- get.toResource
          res <- client.run(req.putHeaders(Authorization(creds)))
        yield res
      }

object ApplicationDefaultCredentials:
  def apply[F[_]: Files: Jwt](client: Client[F], scopes: Seq[String])(
      using F: Temporal[F]): F[GoogleCredentials[F]] =
    val serviceAccountCredentials =
      for
        json <- Files[F]
          .readAll(getWellKnownCredentials)
          .through(text.utf8.decode)
          .compile
          .foldMonoid
        ServiceAccountCredentialsFile(projectId, clientEmail, privateKey) <- parser
          .decode[ServiceAccountCredentialsFile](json)
          .liftTo[F]
        privateKey <- ByteVector.encodeAscii(privateKey).liftTo[F]
        credentials <- ServiceAccountCredentials(
          GoogleOAuth2(client),
          projectId,
          clientEmail,
          privateKey,
          scopes)
      yield credentials

    val computeEngineCredentials = ComputeEngineCredentials(ComputeMetadata(client))

    serviceAccountCredentials.handleErrorWith { e1 =>
      computeEngineCredentials.handleErrorWith { e2 =>
        F.raiseError(CompositeFailure(e1, NonEmptyList.one(e2)))
      }
    }

object ServiceAccountCredentials:
  def apply[F[_]: Temporal](
      oauth2: GoogleOAuth2[F],
      projectId: String,
      clientEmail: String,
      privateKey: ByteVector,
      scopes: Seq[String]): F[GoogleCredentials[F]] =
    OAuth2Credentials(projectId, oauth2.getAccessToken(clientEmail, privateKey, scopes))

final private[auth] case class ServiceAccountCredentialsFile(
    project_id: String,
    client_email: String,
    private_key: String)
    derives Decoder

object ComputeEngineCredentials:
  def apply[F[_]: Temporal](metadata: ComputeMetadata[F]): F[GoogleCredentials[F]] =
    for
      projectId <- metadata.getProjectId
      credentials <- OAuth2Credentials(projectId, metadata.getAccessToken)
    yield credentials

object OAuth2Credentials:
  private[auth] def apply[F[_]](pid: String, refresh: F[AccessToken])(
      using F: Temporal[F]): F[GoogleCredentials[F]] = for
    token <- F.ref(Option.empty[Deferred[F, Either[Throwable, AccessToken]]])
  yield new GoogleCredentials[F]:
    val projectId = pid

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
