package zio.zmx.diagnostics

import zio._

package object fibers {

  type FiberDumpProvider = Has[FiberDumpProvider.Service]

  object FiberDumpProvider {

    trait Service {
      def getFiberDumps: UIO[Iterable[Fiber.Dump]]
    }

    def live(fibersRef: Supervisor[Set[Fiber.Runtime[Any, Any]]]): ULayer[FiberDumpProvider] =
      ZLayer.succeed {
        new Service {
          val getFiberDumps: UIO[Iterable[Fiber.Dump]] =
            fibersRef.value.flatMap { fibers =>
              Fiber.dump(fibers.toSeq: _*)
            }
        }
      }

    val getFiberDumps: URIO[FiberDumpProvider, Iterable[Fiber.Dump]] =
      ZIO.accessM(_.get.getFiberDumps)
  }
}
