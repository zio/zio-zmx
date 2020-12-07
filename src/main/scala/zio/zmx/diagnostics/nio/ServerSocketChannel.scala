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

import java.io.IOException
import java.nio.channels.{ ServerSocketChannel => JServerSocketChannel }

import zio.{ IO, UIO }

class ServerSocketChannel private (val channel: JServerSocketChannel) {

  def bind(addr: InetSocketAddress): IO[IOException, Unit] =
    IO.effect(channel.bind(addr.address)).refineToOrDie[IOException].unit

  def configureBlocking(block: Boolean): IO[IOException, Unit] =
    IO.effect(channel.configureBlocking(block)).unit.refineToOrDie[IOException]

  val validOps: UIO[Integer] =
    IO.effectTotal(channel.validOps())

  def register(sel: Selector, op: Integer): IO[IOException, SelectionKey] =
    IO.effect(new SelectionKey(channel.register(sel.selector, op)))
      .refineToOrDie[IOException]

  val close: IO[Exception, Unit] =
    IO.effect(channel.close()).refineToOrDie[Exception]

  def accept: IO[IOException, SocketChannel] =
    IO.effect(channel.accept()).map(new SocketChannel(_)).refineToOrDie[IOException]
}

object ServerSocketChannel {

  final val open: IO[IOException, ServerSocketChannel] =
    IO.effect(new ServerSocketChannel(JServerSocketChannel.open())).refineToOrDie[IOException]
}
