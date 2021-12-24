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

import cats.effect.IO
import cats.effect.kernel.Deferred
import cats.effect.syntax.all.*
import cats.effect.unsafe.implicits.*
import cats.syntax.all.*
import gcp4s.auth.GoogleCredentials
import gcp4s.auth.GoogleOAuth2
import gcp4s.auth.ServiceAccountCredentials
import gcp4s.auth.ServiceAccountCredentialsFile
import io.circe.parser
import munit.CatsEffectSuite
import org.http4s.client.Client
import org.http4s.client.middleware.RequestLogger
import org.http4s.client.middleware.ResponseLogger
import org.http4s.client.middleware.Retry
import org.http4s.ember.client.EmberClientBuilder

object Gcp4sLiveSuite:
  val scopes = List(
    "https://www.googleapis.com/auth/bigquery",
    "https://www.googleapis.com/auth/trace.append"
  )

  private lazy val setup: IO[(Client[IO], GoogleCredentials[IO], Client[IO])] =
    val deferred =
      Deferred.unsafe[IO, Either[Throwable, (Client[IO], GoogleCredentials[IO], Client[IO])]]
    val resource = for
      ember <- EmberClientBuilder.default[IO].build
      // NEVER log in CI, the output could compromise SERVICE_ACCOUNT_CREDENTIALS
      // .map(RequestLogger(false, false, logAction = Some(IO.println)))
      // .map(ResponseLogger(false, false, logAction = Some(IO.println)))
      ServiceAccountCredentialsFile(projectId, clientEmail, privateKey) <- IO
        .fromEither(
          parser.decode[ServiceAccountCredentialsFile](
            platform.env("SERVICE_ACCOUNT_CREDENTIALS")))
        .toResource
      client = Retry(GoogleRetryPolicy.Default.toRetryPolicy[IO])(
        GoogleSystemParameters[IO]()(ember))
      creds <- ServiceAccountCredentials(
        GoogleOAuth2(client),
        projectId,
        clientEmail,
        privateKey,
        scopes).toResource
      googleClient = creds.middleware(client)
      _ <- deferred.complete(Right((ember, creds, googleClient))).toResource
    yield ()
    resource.useForever.unsafeRunAndForget()
    deferred.get.rethrow

trait Gcp4sLiveSuite extends CatsEffectSuite:
  import Gcp4sLiveSuite.*

  def client = setup.map(_._1)
  def googleCredentials = setup.map(_._2)
  def googleClient = setup.map(_._3)
