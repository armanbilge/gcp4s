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

import cats.Monad
import cats.data.OptionT
import cats.effect.std.Env
import cats.syntax.all.*
import fs2.io.file.Files
import fs2.io.file.Path

private[auth] def getWellKnownCredentials[F[_]: Monad: Env: Files]: F[Path] =
  OptionT(Env[F].get("CLOUDSDK_CONFIG"))
    .map(Path(_))
    .orElse {
      if platform.windows then OptionT(Env[F].get("APPDATA")).map(Path(_) / "gcloud")
      else OptionT.none
    }
    .getOrElseF(
      Files[F].userHome.map(_ / ".config" / "gcloud" / "application_default_credentials.json"))
