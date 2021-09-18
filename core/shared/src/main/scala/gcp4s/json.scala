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

import io.circe.Decoder
import io.circe.Encoder
import io.circe.scodec.decodeByteVector
import io.circe.scodec.encodeByteVector
import scodec.bits.ByteVector

import scala.concurrent.duration.*
import scala.util.Try

object json:
  given Decoder[Long] = Decoder.decodeString.emapTry(s => Try(s.toLong)).or(Decoder.decodeLong)
  given Encoder[Long] = Encoder.encodeString.contramap(_.toString)

  given Decoder[BigInt] = Decoder.decodeString.emapTry(s => Try(BigInt(s)))
  given Encoder[BigInt] = Encoder.encodeString.contramap(_.toString)

  given Decoder[BigDecimal] = Decoder.decodeString.emapTry(s => Try(BigDecimal(s)))
  given Encoder[BigDecimal] = Encoder.encodeString.contramap(_.toString)

  given (using d: Decoder[Long]): Decoder[FiniteDuration] = d.map(_.milliseconds)
  given (using e: Encoder[Long]): Encoder[FiniteDuration] = e.contramap(_.toMillis)

  given Decoder[ByteVector] = decodeByteVector
  given Encoder[ByteVector] = encodeByteVector

  given [A <: Singleton](using A <:< String): Decoder[A] =
    Decoder.decodeString.emapTry(x => Try(x.asInstanceOf[A]))
  given [A <: Singleton](using ev: A <:< String): Encoder[A] =
    Encoder.encodeString.contramap(ev)
