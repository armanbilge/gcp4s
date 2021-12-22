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

import cats.Applicative
import cats.Functor
import cats.effect.std.Random
import cats.syntax.all.*

import scala.concurrent.duration.FiniteDuration

sealed abstract class Sampler[F[_]]:
  def sample: F[Boolean]

object Sampler:
  def always[F[_]: Applicative]: Sampler[F] = new:
    def sample = true.pure

  def never[F[_]: Applicative]: Sampler[F] = new:
    def sample = false.pure

  def randomly[F[_]: Functor: Random](probability: Double): Sampler[F] = new:
    def sample = Random[F].nextDouble.map(_ < probability)
