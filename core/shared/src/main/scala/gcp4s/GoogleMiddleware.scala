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

import cats.effect.SyncIO
import cats.effect.kernel.MonadCancelThrow
import cats.effect.kernel.Temporal
import cats.effect.std.Env
import cats.syntax.all.*
import fs2.io.file.Files
import gcp4s.auth.ApplicationDefaultCredentials
import gcp4s.auth.GoogleCredentials
import gcp4s.auth.GoogleOAuth2
import gcp4s.auth.Jwt
import org.http4s.Query
import org.http4s.Request
import org.http4s.Status
import org.http4s.client.Client
import org.http4s.client.Middleware
import org.http4s.client.middleware.Retry
import org.http4s.client.middleware.RetryPolicy
import org.typelevel.vault.Key.newKey

import java.util.concurrent.ThreadLocalRandom
import scala.concurrent.duration.*

object GoogleMiddleware:
  def apply[F[_]: Temporal: Env: Files: Jwt](scopes: Seq[String])(
      client: Client[F]): F[Client[F]] =
    val googleClient =
      Retry(GoogleRetryPolicy.Default.toRetryPolicy[F])(GoogleSystemParameters[F]()(client))
    for credentials <- ApplicationDefaultCredentials[F](googleClient, scopes)
    yield credentials.middleware(googleClient)

/**
 * @see
 *   [[https://cloud.google.com/apis/docs/system-parameters]]
 */
object GoogleSystemParameters:
  val QuotaUser = newKey[SyncIO, String].unsafeRunSync()
  val PrettyPrint = newKey[SyncIO, Boolean].unsafeRunSync()

  def apply[F[_]: MonadCancelThrow](
      quotaUser: Option[String] = None,
      prettyPrint: Boolean = false
  ): Middleware[F] = client =>
    Client[F] { req =>
      val qu = req.attributes.lookup(QuotaUser).orElse(quotaUser)
      val pp = req.attributes.lookup(PrettyPrint).getOrElse(prettyPrint)
      val newReq = req.withUri(
        req.uri.withOptionQueryParam("quotaUser", qu).withQueryParam("prettyPrint", pp)
      )
      client.run(newReq)
    }

enum GoogleRetryPolicy:
  case Disabled
  case ExponentialBackoff(
      maxRetries: Int,
      minBackoff: FiniteDuration,
      maxBackoff: FiniteDuration,
      randomFactor: Double,
      status: Set[Status],
      reckless: Boolean
  )

  final def toRetryPolicy[F[_]]: RetryPolicy[F] =
    (req, res, attempt) => {
      req
        .attributes
        .lookup(GoogleRetryPolicy.Key)
        .getOrElse(identity[GoogleRetryPolicy])
        .apply(this) match
        case ExponentialBackoff(
              maxRetries,
              minBackoff,
              maxBackoff,
              randomFactor,
              status,
              reckless)
            if (reckless || req.method.isIdempotent) && RetryPolicy.isErrorOrStatus(
              res,
              status) =>
          RetryPolicy.exponentialBackoff(maxBackoff, maxRetries)(attempt).flatMap { duration =>
            Some(
              duration.max(minBackoff) * ThreadLocalRandom.current().nextDouble(randomFactor))
              .collect { case duration: FiniteDuration => duration }
          }
        case _ => None
    }

object GoogleRetryPolicy:
  val Key = newKey[SyncIO, GoogleRetryPolicy => GoogleRetryPolicy].unsafeRunSync()

  import Status.*
  val Default: ExponentialBackoff = ExponentialBackoff(
    6,
    1.second,
    1.minute,
    0.2,
    Set(
      TooManyRequests,
      InternalServerError,
      BadGateway,
      ServiceUnavailable,
      GatewayTimeout
    ),
    false
  )
