package zio.zmx

import com.github.ghik.silencer.silent
import zio._
import zio.internal.Platform
import zio.Supervisor.Propagation

import scala.collection.JavaConverters._

package object diagnostics {

  val ZMXSupervisor: Supervisor[Set[Fiber.Runtime[Any, Any]]] =
    new Supervisor[Set[Fiber.Runtime[Any, Any]]] {

      private[this] val fibers =
        Platform.newConcurrentSet[Fiber.Runtime[Any, Any]]()

      val value: UIO[Set[Fiber.Runtime[Any, Any]]] =
        UIO.succeedNow(fibers.asScala.toSet: @silent("JavaConverters"))

      def unsafeOnStart[R, E, A](
        environment: R,
        effect: ZIO[R, E, A],
        parent: Option[Fiber.Runtime[Any, Any]],
        fiber: Fiber.Runtime[E, A]
      ): Propagation = {
        fibers.add(fiber)
        Propagation.Continue
      }

      def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Propagation = {
        fibers.remove(fiber)
        Propagation.Continue
      }
    }
}
