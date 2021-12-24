package zio

package object zmx {
  def spawn(i: Int): UIO[Unit] =
    (1 to i).foldLeft(ZIO.unit) { case (fa, _) =>
      ZIO.unit.fork.flatMap(f => fa.fork.flatMap(_.join) *> f.join )
    }

  def deep(size: Int) =
    for {
      _ <- spawn(size)
    } yield ()

  def broad(size: Int) =
    for {
      _ <- ZIO.foreachParDiscard(1 to size)(_ => ZIO.never.fork)
    } yield ()

  def mixed(size: Int) =
    for {
      _ <- ZIO.foreachParDiscard(1 to (size / 100))(_ => spawn(size / 100))
    } yield ()

  val ZMXSupervisor = zio.zmx.diagnostics.ZMXSupervisor
  val ZMXRuntime    = zio.Runtime.default.mapPlatform(_.withSupervisor(ZMXSupervisor))
}
