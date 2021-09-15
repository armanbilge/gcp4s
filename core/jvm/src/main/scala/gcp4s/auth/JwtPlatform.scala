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

package gcp4s.auth

import cats.MonadThrow
import cats.effect.kernel.Clock
import cats.syntax.all.*
import scodec.bits.ByteVector
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.Signature

abstract private[auth] class JwtCompanionPlatform:
  given [F[_]: Clock](using F: MonadThrow[F]): Jwt[F] =
    new UnsealedJwt:
      def sign(data: ByteVector, privateKey: ByteVector): F[ByteVector] =
        F.catchNonFatal {
          val keyFactory = KeyFactory.getInstance("RSA")
          val spec = new PKCS8EncodedKeySpec(privateKey.toArray)
          keyFactory.generatePrivate(spec)
          val signer = Signature.getInstance("SHA256withRSA")
          signer.initSign(keyFactory.generatePrivate(spec))
          signer.update(data.toByteBuffer)
          ByteVector.view(signer.sign)
        }
