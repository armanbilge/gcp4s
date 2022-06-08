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
import cats.effect.kernel.Concurrent
import cats.effect.kernel.MonadCancelThrow
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.CompositeFailure
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.text
import io.circe.Decoder
import io.circe.jawn
import org.http4s.AuthScheme
import org.http4s.Credentials
import org.http4s.client.Client
import org.http4s.client.Middleware
import org.http4s.headers.Authorization
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
  def apply[F[_]: Clock: Files: Jwt](client: Client[F], scopes: Seq[String])(
      using F: Concurrent[F]): F[GoogleCredentials[F]] =
    val serviceAccountCredentials =
      for
        json <- Files[F]
          .readAll(getWellKnownCredentials)
          .through(text.utf8.decode)
          .compile
          .foldMonoid
        ServiceAccountCredentialsFile(projectId, clientEmail, privateKey) <- jawn
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

  def apply[F[_]: Concurrent: Clock](
      oauth2: GoogleOAuth2[F],
      projectId: String,
      clientEmail: String,
      privateKey: ByteVector,
      scopes: Seq[String]): F[GoogleCredentials[F]] =
    OAuth2Credentials(projectId, oauth2.getAccessToken(clientEmail, privateKey, scopes))

  def apply[F[_]: Concurrent: Clock](
      oauth2: GoogleOAuth2[F],
      projectId: String,
      clientEmail: String,
      privateKey: String,
      scopes: Seq[String]): F[GoogleCredentials[F]] =
    for
      privateKey <- parseKey(privateKey).liftTo[F]
      credentials <- OAuth2Credentials(
        projectId,
        oauth2.getAccessToken(clientEmail, privateKey, scopes))
    yield credentials

  private def parseKey(key: String): Either[Throwable, ByteVector] =
    ByteVector
      .fromBase64Descriptive(
        key
          .replaceAll("-----BEGIN (.*)-----", "")
          .replaceAll("-----END (.*)-----", "")
          .replaceAll("\r\n", "")
          .replaceAll("\n", "")
          .trim)
      .leftMap(new RuntimeException(_))

final case class ServiceAccountCredentialsFile(
    project_id: String,
    client_email: String,
    private_key: String)

object ServiceAccountCredentialsFile:
  given Decoder[ServiceAccountCredentialsFile] =
    Decoder.forProduct3("product_id", "client_email", "private_key")(
      ServiceAccountCredentialsFile(_, _, _))

object ComputeEngineCredentials:
  def apply[F[_]: Concurrent: Clock](metadata: ComputeMetadata[F]): F[GoogleCredentials[F]] =
    for
      projectId <- metadata.getProjectId
      credentials <- OAuth2Credentials(projectId, metadata.getAccessToken)
    yield credentials

object OAuth2Credentials:
  private[auth] def apply[F[_]: Clock](pid: String, refresh: F[AccessToken])(
      using F: Concurrent[F]): F[GoogleCredentials[F]] =
    for token <- F.ref(Option.empty[F[AccessToken]])
    yield new GoogleCredentials[F]:
      val projectId = pid

      def get = for AccessToken(token, _) <- getToken
      yield Credentials.Token(AuthScheme.Bearer, token)

      def getToken: F[AccessToken] = OptionT(token.get)
        .semiflatMap(identity)
        .flatMapF { token =>
          for expired <- token.expiresSoon()
          yield Option.unless(expired)(token)
        }
        .getOrElseF {
          for
            memo <- F.memoize(refresh)
            updated <- token.tryUpdate(_ => Some(memo))
            token <-
              if updated then memo
              else getToken
          yield token
        }
