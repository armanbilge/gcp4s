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
package trace

import cats.parse.Parser
import cats.parse.Rfc5234.*
import cats.syntax.all.*
import scodec.bits.ByteVector

import java.lang

final private case class `X-Cloud-Trace-Context`(traceId: ByteVector, spanId: Long):
  override def toString = s"${traceId.toHex}/${lang.Long.toUnsignedString(spanId)}"

private object `X-Cloud-Trace-Context`:
  inline val name = "X-Cloud-Trace-Context"

  def parse(s: String) = parser.parseAll(s)

  private val parser = for
    (traceId, spanId) <- (hexdig
      .repExactlyAs[String](32) ~ (Parser.char('/') *> digit.repAs[String](1, 20)))
    traceId <- ByteVector.fromHex(traceId).fold(Parser.fail)(Parser.pure)
    spanId <- Either
      .catchNonFatal(lang.Long.parseUnsignedLong(spanId))
      .fold(e => Parser.failWith(e.getMessage), Parser.pure)
  yield `X-Cloud-Trace-Context`(traceId, spanId)
