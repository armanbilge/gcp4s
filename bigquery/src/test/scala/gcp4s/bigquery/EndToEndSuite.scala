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
package bigquery

import cats.effect.IO

class EndToEndSuite extends Gcp4sLiveSuite {

  val dsl = googleClient.map(BigQueryDsl(_))

  test("projects contains project id") {
    dsl.flatMap { dsl =>
      import dsl.*
      import dsl.given

      for
        projectList <- projects.compile.toVector
        projectIds = projectList.flatMap(_.projects.toVector.flatten).flatMap(_.id)
        creds <- googleCredentials
      yield assert(projectIds.contains(creds.projectId))
    }
  }

}
