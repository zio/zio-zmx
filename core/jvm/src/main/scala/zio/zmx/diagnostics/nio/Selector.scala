/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package zio.zmx.diagnostics.nio

import zio.IO

import java.io.IOException
import java.nio.channels.{ ClosedSelectorException, Selector => JSelector, SelectionKey => JSelectionKey }
import scala.collection.JavaConverters._

class Selector private (val selector: JSelector) {

  val selectedKeys: IO[ClosedSelectorException, Set[SelectionKey]] =
    IO.effect(selector.selectedKeys())
      .map(_.asScala.toSet[JSelectionKey].map(new SelectionKey(_)))
      .refineToOrDie[ClosedSelectorException]

  def removeKey(key: SelectionKey): IO[ClosedSelectorException, Unit] =
    IO.effect(selector.selectedKeys().remove(key.selectionKey))
      .unit
      .refineToOrDie[ClosedSelectorException]

  val select: IO[Exception, Int] =
    IO.effect(selector.select()).refineToOrDie[IOException]

  val close: IO[IOException, Unit] =
    IO.effect(selector.close()).refineToOrDie[IOException].unit
}

object Selector {

  val make: IO[IOException, Selector] =
    IO.effect(new Selector(JSelector.open())).refineToOrDie[IOException]
}
