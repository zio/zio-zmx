package zio.zmx.diagnostics.nio

import java.io.IOException
import java.nio.channels.{ClosedSelectorException, Selector => JSelector, SelectionKey => JSelectionKey}

import zio.IO

import scala.jdk.CollectionConverters._

class Selector(private[nio] val selector: JSelector) {

  final val selectedKeys: IO[ClosedSelectorException, Set[SelectionKey]] =
    IO.effect(selector.selectedKeys())
      .map(_.asScala.toSet[JSelectionKey].map(new SelectionKey(_)))
      .refineToOrDie[ClosedSelectorException]

  final def removeKey(key: SelectionKey): IO[ClosedSelectorException, Unit] =
    IO.effect(selector.selectedKeys().remove(key.selectionKey))
      .unit
      .refineToOrDie[ClosedSelectorException]

  final val select: IO[Exception, Int] =
    IO.effect(selector.select()).refineToOrDie[IOException]
}

object Selector {

  final val make: IO[IOException, Selector] =
    IO.effect(new Selector(JSelector.open())).refineToOrDie[IOException]
}