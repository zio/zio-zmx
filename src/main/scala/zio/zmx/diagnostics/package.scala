package zio.zmx

import java.util.concurrent.atomic.AtomicReference

import zio._
import zio.Supervisor.Propagation

import zio.zmx.diagnostics.graph._

package object diagnostics {

  val ZMXSupervisor: Supervisor[Set[Fiber.Runtime[Any, Any]]] =
    new Supervisor[Set[Fiber.Runtime[Any, Any]]] {

      private[this] val graphRef: AtomicReference[Graph[Fiber.Runtime[Any, Any], String, String]] = new AtomicReference(
        Graph.empty[Fiber.Runtime[Any, Any], String, String]
      )

      val value: UIO[Set[Fiber.Runtime[Any, Any]]] =
        UIO(graphRef.get.nodes.map(_.node))

      def unsafeOnStart[R, E, A](
        environment: R,
        effect: ZIO[R, E, A],
        parent: Option[Fiber.Runtime[Any, Any]],
        fiber: Fiber.Runtime[E, A]
      ): Propagation = {
        graphRef.updateAndGet { (m: Graph[Fiber.Runtime[Any, Any], String, String]) =>
          val n = m.addNode(Node(fiber, s"#${fiber.id.seqNumber}"))
          parent match {
            case Some(parent) => n.addEdge(Edge(parent, fiber, s"#${parent.id.seqNumber} -> #${fiber.id.seqNumber}"))
            case None         => n
          }
        }

        Propagation.Continue
      }

      def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Propagation = {
        graphRef.updateAndGet((m: Graph[Fiber.Runtime[Any, Any], String, String]) =>
          if (m.successors(fiber).isEmpty)
            m.removeNode(fiber)
          else
            m
        )

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
  type CoreMetrics = Has[CoreMetrics.Service]

  object CoreMetrics {
    trait Service {
      def enable: UIO[Unit]

      def disable: UIO[Unit]

      def isEnabled: UIO[Boolean]
    }

    val enable: ZIO[CoreMetrics, Nothing, Unit] = ZIO.accessM[CoreMetrics](_.get.enable)

    val disable: ZIO[CoreMetrics, Nothing, Unit] = ZIO.accessM[CoreMetrics](_.get.disable)

    val isEnabled: ZIO[CoreMetrics, Nothing, Boolean] = ZIO.accessM[CoreMetrics](_.get.isEnabled)

    /**
     * The `CoreMetrics` service installs hooks into ZIO runtime system to track
     * important core metrics, such as number of fibers, fiber status, fiber
     * lifetimes, etc.
     */
//    def live: ZLayer[Metrics, Nothing, CoreMetrics] = ???
  }

}
