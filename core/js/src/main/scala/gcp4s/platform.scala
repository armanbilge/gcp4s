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

import fs2.io.file.Path

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

private[gcp4s] object platform:
  def env = process.env
  def home = Path(os.homedir())
  def windows = os.platform() == "wind32"

@js.native
@JSImport("os", JSImport.Default)
private[gcp4s] object os extends js.Object:
  def homedir(): String = js.native
  def platform(): String = js.native

@js.native
@JSImport("process", JSImport.Default)
private[gcp4s] object process extends js.Object:
  def env: js.Dictionary[String] = js.native
