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

import fs2.io.file.Path

private[auth] def getWellKnownCredentials: Path =
  platform
    .env
    .get("CLOUDSDK_CONFIG")
    .map(Path(_))
    .orElse {
      Option.when(platform.windows) {
        Path(platform.env("APPDATA")) / "gcloud"
      }
    }
    .getOrElse(platform.home / ".config" / "gcloud") / "application_default_credentials.json"
