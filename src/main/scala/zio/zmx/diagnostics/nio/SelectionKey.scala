package zio.zmx.diagnostics.nio

import java.nio.channels.{CancelledKeyException, SelectionKey => JSelectionKey, SelectableChannel => JSelectableChannel}

import zio.{IO, UIO}

class SelectionKey(val selectionKey: JSelectionKey) {

  val channel: UIO[JSelectableChannel] =
    IO.effectTotal(selectionKey.channel())

  def isAcceptable: IO[CancelledKeyException, Boolean] =
    IO.effect(selectionKey.isAcceptable).refineOrDie {
      case e: CancelledKeyException => e
    }

  def isReadable: IO[CancelledKeyException, Boolean] =
    IO.effect(selectionKey.isReadable).refineOrDie {
      case e: CancelledKeyException => e
    }
}