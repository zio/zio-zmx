package zio.zmx.prometheus

import zio.ExitCode

import zio.console._

object PrometheusEncodeSample extends zio.App {

  override def run(args: List[String]): zio.URIO[zio.ZEnv, ExitCode] = for {
    _ <- putStrLn("Hello World ")
  } yield (ExitCode.success)

}
