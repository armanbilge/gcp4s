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

package gcp4s.trace

import munit.ScalaCheckSuite
import org.scalacheck.Prop.forAll
import scodec.bits.ByteVector
import scodec.bits.hex

class XCloudTraceContextSuite extends ScalaCheckSuite:

  test("example") {
    val parsed = `X-Cloud-Trace-Context`.parse("105445aa7843bc8bf206b12000100000/1;o=1")
    val expected = `X-Cloud-Trace-Context`(
      hex"105445aa7843bc8bf206b12000100000",
      1
    )
    assertEquals(parsed, Some(expected))
  }

  property("round-trip") {
    forAll { (traceId1: Long, traceId2: Long, spanId: Long) =>
      val header = `X-Cloud-Trace-Context`(ByteVector(traceId1, traceId2), spanId)
      val rendered = header.toString
      val parsed = `X-Cloud-Trace-Context`.parse(rendered)
      assertEquals(parsed, Some(header))
    }
  }
