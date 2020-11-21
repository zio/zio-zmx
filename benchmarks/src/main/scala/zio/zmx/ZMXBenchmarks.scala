package zio.zmx

object ZMXBenchmarks {
  val Runtime = zio.Runtime.default.mapPlatform(_.withSupervisor(zio.zmx.ZMXSupervisor))
}
