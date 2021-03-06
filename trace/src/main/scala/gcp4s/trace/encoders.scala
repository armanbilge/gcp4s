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

import cats.syntax.all.*
import gcp4s.trace.model.AttributeValue
import gcp4s.trace.model.StackFrame
import gcp4s.trace.model.StackFrames
import gcp4s.trace.model.StackTrace
import gcp4s.trace.model.TruncatableString
import natchez.TraceValue

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

private def encodeAttributes(attributes: Map[String, TraceValue]): model.Attributes =
  import TraceValue.*

  val serialized = attributes
    .view
    .collect {
      case (l, v) if l.nonEmpty && l.length <= 128 =>
        // Natchez uses '.' delimiter where Cloud Trace uses '/'
        val label = if l.charAt(0) == '/' then l else "/" + l.replace('.', '/')

        val value = v match
          case StringValue(s) =>
            AttributeValue(stringValue = encodeTruncatableString(s, 256).some)
          case NumberValue(n) => AttributeValue(intValue = n.longValue.some)
          case BooleanValue(b) => AttributeValue(boolValue = b.some)

        label -> value
    }
    .take(32)
    .toMap

  model.Attributes(
    attributeMap = serialized.some,
    droppedAttributesCount = Some(attributes.size - serialized.size))

private def encodeStackTrace(t: Throwable): StackTrace =
  val stackFrames = Option(t.getStackTrace).map { st =>
    st.map { ste =>
      StackFrame(
        functionName = encodeTruncatableString(ste.getMethodName, 1024).some,
        fileName = encodeTruncatableString(ste.getFileName, 256).some,
        lineNumber = ste.getLineNumber.toLong.some
      )
    }.toList
  }

  StackTrace(
    stackTraceHashId = t.hashCode.toLong.some,
    stackFrames = StackFrames(frame = stackFrames).some)

private def encodeTruncatableString(s: String, maxBytes: Int): TruncatableString =
  if s.length <= 4 * maxBytes then
    TruncatableString(value = s.some, truncatedByteCount = 0.some)
  else
    import StandardCharsets.UTF_8
    val b = s.getBytes(UTF_8)
    if b.length <= maxBytes then TruncatableString(value = s.some, truncatedByteCount = 0.some)
    else
      val bb = ByteBuffer.wrap(b, 0, maxBytes)
      val cb = CharBuffer.allocate(maxBytes)
      val dec = UTF_8.newDecoder()
      dec.onMalformedInput(CodingErrorAction.IGNORE)
      dec.decode(bb, cb, true)
      dec.flush(cb)
      val truncated = new String(cb.array, 0, cb.position())
      TruncatableString(
        value = truncated.some,
        truncatedByteCount = Some(b.length - truncated.getBytes(UTF_8).length))
