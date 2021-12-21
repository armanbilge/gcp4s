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
import scala.util.Try

final private case class `X-Cloud-Trace-Context`(
    traceId: ByteVector,
    spanId: Long,
    force: Option[Boolean]):
  
  override def toString =
    val o = force.fold("")(f => s";o=${if f then '1' else '0'}")
    s"${traceId.toHex}/${lang.Long.toUnsignedString(spanId)}$o"
  
  def toHeader = `X-Cloud-Trace-Context`.name -> toString

private object `X-Cloud-Trace-Context`:
  inline val name = "X-Cloud-Trace-Context"

  def parse(s: String): Option[`X-Cloud-Trace-Context`] =
    parser.parseAll(s).toOption.flatMap { case ((traceId, spanId), force) =>
      (
          ByteVector.fromHex(traceId),
          Try(lang.Long.parseUnsignedLong(spanId)).toOption,
          force.flatten.collect {
            case '0' => false
            case '1' => true
          }.some
        ).mapN(`X-Cloud-Trace-Context`(_, _, _))
    }

  private val parser =
    hexdig.repExactlyAs[String](32) ~
      (Parser.char('/') *> digit.repAs[String](1, 20)) ~
      (Parser.char(';') *> (Parser.string("o=") *> Parser.charIn('0', '1')).?).?
