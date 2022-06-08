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

import cats.effect.IO
import cats.syntax.all.*
import munit.CatsEffectSuite
import munit.Location
import munit.ScalaCheckEffectSuite
import org.http4s.Credentials
import org.scalacheck.effect.PropF

import scala.concurrent.duration.*

class GoogleCredentialsSuite extends CatsEffectSuite, ScalaCheckEffectSuite, Gcp4sLiveSuite:

  given Location = Location.empty

  test("queue requests until token arrives, then respond") {
    PropF.forAllF { (x: List[Unit]) =>
      for
        deferred <- IO.deferred[AccessToken]
        credentials <- OAuth2Credentials("projectId", deferred.get)
        now <- IO.realTime
        token = AccessToken("token", now + 1.hour)
        _ <- (IO.sleep(10.milliseconds) *> deferred.complete(token)).start
        requests <- x.parTraverse(_ => credentials.get)
      yield assert(requests.size == x.size)
    }
  }

  test("successfully obtain a live access token".ignore) {
    googleCredentials.flatMap(_.get)
  }
