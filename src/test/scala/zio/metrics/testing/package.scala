package zio.metrics.connectors

import scala.{Console => SConsole}

import zio._
import zio.test._

package object testing {

  def elapasedTime[R, E, A](label: String)(zio: ZIO[R, E, A]) = {
    def pretty(d: Duration) = s"${d.toMillis}ms"

    zio
      .timedWith(ZIO.attempt(java.lang.System.nanoTime()))
      .flatMap { case (elapsed, a) => Console.printLine(s"::: [$label]> Elapsed: ${pretty(elapsed)}.").as(a) }

  }

  def ignoreIf(ignored: => Boolean, reason: => String) =
    new TestAspectAtLeastR[Annotations] {
      def some[R <: Annotations, E](spec: Spec[R, E])(implicit trace: Trace): Spec[R, E] = {
        if (ignored)
          println(
            s"""|${SConsole.YELLOW}*************** WARNING: Your Test Was Conditionally Ignored *******************
                |*
                |* REASON: $reason 
                |*
                |********************************************************************************${SConsole.RESET}""".stripMargin,
          )

        spec.when(!ignored)
      }
    }

}
