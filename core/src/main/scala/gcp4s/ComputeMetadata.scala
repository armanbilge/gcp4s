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

import org.http4s.Header
import org.typelevel.ci.given
import org.http4s.Request
import org.http4s.Uri
import org.http4s.syntax.all.*
import org.http4s.Uri.Path
import gcp4s.auth.AccessToken
import org.http4s.client.Client
import org.http4s.Headers
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Clock

trait ComputeMetadata[F[_]]:
  def getProjectId: F[String]
  def getZone: F[String]
  def getInstanceId: F[String]
  def getClusterName: F[String]
  def getContainerName: F[String]
  def getNamespaceId: F[String]
  def getAccessToken: F[AccessToken]

object ComputeMetadata:

  given [F[_]: Concurrent: Clock](using client: Client[F]): ComputeMetadata[F] with
    val `Metadata-Flavor` = Header.Raw(ci"Metadata-Flavor", "Google")
    val headers = Headers(`Metadata-Flavor`)
    val baseUri: Uri = uri"http://metadata.google.internal/computeMetadata/v1"
    def mkRequest(path: String) = Request[F](uri = baseUri / path, headers = headers)

    def get(path: String): F[String] = client.expect[String](mkRequest(path))

    val getProjectId: F[String] = get("project/project-id")
    val getZone: F[String] = get("instance/zone")
    val getInstanceId: F[String] = get("instance/id")
    val getClusterName: F[String] = get("instance/attributes/cluster-name")
    val getContainerName: F[String] = get("instance/attributes/container-name")
    val getNamespaceId: F[String] = get("instance/attributes/namespace-id")
    val getAccessToken: F[AccessToken] =
      client.expect("instance/service-accounts/default/token")
