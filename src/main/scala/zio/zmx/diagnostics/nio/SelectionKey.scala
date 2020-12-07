package zio.zmx.diagnostics.nio

import java.nio.channels.{CancelledKeyException, SelectionKey => JSelectionKey, SelectableChannel => JSelectableChannel}

import zio.{IO, UIO}

/**
 * TODO
 *
 * @author alesavin
 */
class SelectionKey(private[nio] val selectionKey: JSelectionKey) {

  final val channel: UIO[JSelectableChannel] =
    IO.effectTotal(selectionKey.channel())

  final def isAcceptable: IO[CancelledKeyException, Boolean] =
    IO.effect(selectionKey.isAcceptable).refineOrDie {
      case e: CancelledKeyException => e
    }

  final def isReadable: IO[CancelledKeyException, Boolean] =
    IO.effect(selectionKey.isReadable()).refineOrDie {
      case e: CancelledKeyException => e
    }
}