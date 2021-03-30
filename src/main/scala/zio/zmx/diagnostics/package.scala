package zio.zmx

import scala.collection.JavaConverters._

import zio._
import zio.internal.Platform
import zio.Supervisor.Propagation
import com.github.ghik.silencer.silent

package object diagnostics {

  type Diagnostics = Has[Diagnostics.Service]

  val ZMXSupervisor: Supervisor[Set[Fiber.Runtime[Any, Any]]] =
    new Supervisor[Set[Fiber.Runtime[Any, Any]]] {

      private[this] val fibers =
        Platform.newConcurrentSet[Fiber.Runtime[Any, Any]]()

      val value: UIO[Set[Fiber.Runtime[Any, Any]]] = {
        val locals = fibers.asScala: @silent("JavaConverters")
        UIO.succeedNow(locals.toSet)
      }

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

  type Diagnostics = Has[Diagnostics.Service]

  object Diagnostics {
    trait Service {}

    /**
     * The Diagnostics service will listen on the specified port for commands to perform fiber
     * dumps, either across all fibers or across the specified fiber ids.
     */
    def live(host: String, port: Int): ZLayer[ZEnv, Throwable, Diagnostics] =
      ZLayer.fromManaged(
        ZMXServer
          .make(ZMXConfig(host, port, true))
          .as(new Service {})
      )
  }

  // TODO Does this needs to be part of ZIO-Core?
  // type CoreMetrics = Has[CoreMetrics.Service]

// TODO: Implement some core ZIO metrics
//   object CoreMetrics {
//     trait Service {
//       def enable: UIO[Unit]
//
//       def disable: UIO[Unit]
//
//       def isEnabled: UIO[Boolean]
//     }
//
//     val enable: ZIO[CoreMetrics, Nothing, Unit] = ZIO.accessM[CoreMetrics](_.get.enable)
//
//     val disable: ZIO[CoreMetrics, Nothing, Unit] = ZIO.accessM[CoreMetrics](_.get.disable)
//
//     val isEnabled: ZIO[CoreMetrics, Nothing, Boolean] = ZIO.accessM[CoreMetrics](_.get.isEnabled)
//
//     /**
//      * The `CoreMetrics` service installs hooks into ZIO runtime system to track
//      * important core metrics, such as number of fibers, fiber status, fiber
//      * lifetimes, etc.
//      */
//     def live: ZLayer[Metrics, Nothing, CoreMetrics] = ???
//   }

}
