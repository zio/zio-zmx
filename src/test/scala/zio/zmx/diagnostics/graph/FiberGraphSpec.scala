package zio.zmx.diagnostics.graph

import zio.test._
import zio._
import zio.test.environment._
import java.time.Duration
import zio.test.Assertion._
//import zio.random._
import zio.duration._
//import zio.test.AssertionM._
import zio.test.TestAspect._

object FiberGraphSpec extends DefaultRunnableSpec {

  def spec: ZSpec[
    Annotations with Live with Sized with TestClock with TestConfig with TestConsole with TestRandom with TestSystem with ZEnv,
    Any
  ] =
    suite("ZMX Supervisor Spec")(
      suite("dump suite")(
        testM("multiple fiber, dump one") {
          for {
            a          <- (ZIO.sleep(Duration.ofSeconds(1)) *> console.putStrLn("something1")).fork
            b          <- (ZIO.sleep(Duration.ofSeconds(1)) *> console.putStrLn("something2")).fork
            c          <- (ZIO.sleep(Duration.ofSeconds(1)) *> console.putStrLn("something3")).fork
            d          <- (ZIO.sleep(Duration.ofSeconds(1)) *> console.putStrLn("something4")).fork
            graphRef   <- Ref.make(Graph.empty[Fiber.Runtime[Any, Any], String, String])
            _          <- graphRef.update(g => g.addNode(Node(a, "a")))
            _          <- graphRef.update(g => g.addNode(Node(b, "b")))
            _          <- graphRef.update(g => g.addNode(Node(c, "c")))
            _          <- graphRef.update(g => g.addNode(Node(d, "d")))
            fiberGraph <- UIO.succeed(FiberGraph.apply(graphRef))
            dumped     <- fiberGraph.dump(a.id)
            toMatch    <- a.dump
          } yield assert(dumped.fiberId)(equalTo(toMatch.fiberId))
        },
        testM("one fiber, dump one") {
          for {
            a          <- ZIO.effect(println("hey ho")).delay(1.second).forever.fork
            dump       <- a.dump
            graphRef   <- Ref.make(Graph.empty[Fiber.Runtime[Any, Any], String, String])
            _          <- graphRef.update(g => g.addNode(Node(a, "a")))
            fiberGraph <- UIO.succeed(FiberGraph.apply(graphRef))
            d          <- fiberGraph.dump(a.id)
          } yield assert(d.fiberId)(equalTo(dump.fiberId))
        },
        testM("dump all") {
          for {
            fg     <- fiberGraph
            f1     <- fiber1
            f2     <- fiber2
            d1     <- f1.dump.map(_.fiberId)
            d2      <- f2.dump.map(_.fiberId)
            chunkF  <- fg.dumpAll.runCollect.map(ch => ch.map(_.fiberId))
          } yield assert(chunkF.toIterable)(contains(d1)) && assert(chunkF.toIterable)(contains(d2))
        },
        testM("ignore test") {
          ???
        } @@ ignore
      )
    )

  val fiberGraph = for {
    graphRef   <- Ref.make(Graph.empty[Fiber.Runtime[Any, Any], String, String])
    f1         <- fiber1
    f2         <- fiber2
    _          <- graphRef.update(g => g.addNode(Node(f1, "")))
    _          <- graphRef.update(g => g.addNode(Node(f2, "")))
    fiberGraph <- UIO.succeed(FiberGraph.apply(graphRef))
  } yield (fiberGraph)

  val fiber1 = ZIO.succeed(42).forever.fork
  val fiber2 = ZIO.sleep(Duration.ofMinutes(1)).fork
}
