package zio.zmx.diagnostics

import zio._
import zio.clock._
import zio.console._

trait Diagnostics

object Diagnostics {

  /**
   * The Diagnostics service will listen on the specified port for commands to perform fiber
   * dumps, either across all fibers or across the specified fiber ids.
   */
  def make(host: String, port: Int): ZLayer[Clock with Console, Exception, Has[Diagnostics]] =
    (ZLayer.succeed(ZMXConfig(host, port, false)) ++ ZLayer
      .requires[Has[Clock.Service] with Has[Console.Service]]) >>> live

  val live: ZLayer[Has[Console.Service] with Has[Clock.Service] with Has[ZMXConfig], Exception, Has[Diagnostics]] =
    (for {
      config         <- ZManaged.service[ZMXConfig]
      diagnosticsLive = DiagnosticsLive(config)
      shutdown       <- diagnosticsLive.initialize.toManaged_
      _              <- ZManaged.finalizer(shutdown)
    } yield diagnosticsLive).toLayer
}
