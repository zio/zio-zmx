package zio.zmx.diagnostics.nio

import java.net.{InetSocketAddress => JInetSocketAddress}

import zio.IO

final class InetSocketAddress private (val address: JInetSocketAddress)
object InetSocketAddress {

  def apply(host: String, port: Int): IO[Exception, InetSocketAddress] =
    IO.effect(new JInetSocketAddress(host, port))
      .refineToOrDie[Exception]
      .map(new InetSocketAddress(_))
}

