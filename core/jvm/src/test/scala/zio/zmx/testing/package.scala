package zio.zmx

import zio._

package object testing {

  def elapasedTime[R, E, A](label: String)(zio: ZIO[R, E, A]) = {
    def pretty(d: Duration) = s"${d.toMillis}ms"

    zio
      .timedWith(ZIO.attempt(java.lang.System.nanoTime()))
      .flatMap { case (elapsed, a) => Console.printLine(s"::: [$label]> Elapsed: ${pretty(elapsed)}.").as(a) }
  }
}
