package zio.zmx.diagnostics.graph

import zio.test._
import zio._
import zio.test.environment._
import java.time.Duration
import zio.test.Assertion._
import zio.duration._


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
            bf           <- ZIO.never.fork
            af           <- ZIO.never.fork
            cf           <- ZIO.never.fork
            df           <- ZIO.never.fork
            graphRef     <- Ref.make(Graph.empty[Fiber.Runtime[Any, Any], String, String])
            _            <- graphRef.update(g => g.addNode(Node(af, "a")))
            _            <- graphRef.update(g => g.addNode(Node(bf, "b")))
            _            <- graphRef.update(g => g.addNode(Node(cf, "c")))
            _            <- graphRef.update(g => g.addNode(Node(df, "d")))
            fiberGraph   <- UIO.succeed(FiberGraph.apply(graphRef))
            chunk        <- fiberGraph.dumpAll.runCollect
            (a, b, c, d) <- for {
                              a <- af.dump.map(_.fiberId)
                              b <- bf.dump.map(_.fiberId)
                              c <- cf.dump.map(_.fiberId)
                              d <- df.dump.map(_.fiberId)
                            } yield (a, b, c, d)
          } yield assert(chunk)(hasSize(equalTo(4))) &&
            assert(chunk.map(_.fiberId))(contains(a)) &&
            assert(chunk.map(_.fiberId))(contains(b)) &&
            assert(chunk.map(_.fiberId))(contains(c)) &&
            assert(chunk.map(_.fiberId))(contains(d))
        },
        testM("dump up to level of 0") {
          for {
            g          <- createGraph
            fiberGraph <- Ref.make(g).map(graphRef => FiberGraph.apply(graphRef))
            dumpsAll   <- fiberGraph.dumpAll.runCollect
            a1          = g.nodes.find(_.label == "a1").get.node
            dump       <- fiberGraph.dump(a1.id)
            chunkOfOne <- fiberGraph.dump(a1.id, 0).runCollect
          } yield assert(dumpsAll)(hasSize(equalTo(8))) &&
            assert(chunkOfOne)(hasSize(equalTo(1))) &&
            assert(chunkOfOne.head.fiberId.seqNumber)(equalTo(dump.fiberId.seqNumber)) &&
            assert(dumpsAll.map(_.fiberId.seqNumber))(contains(dump.fiberId.seqNumber))
        },
        testM("dump up to level 1") {
          for {
            g          <- createGraph
            fiberGraph <- Ref.make(g).map(graphRef => FiberGraph.apply(graphRef))
            a1          = g.nodes.find(_.label == "a1").get.node
            a2          = g.nodes.find(_.label == "a2").get.node
            a3          = g.nodes.find(_.label == "a3").get.node
            a4          = g.nodes.find(_.label == "a4").get.node
            chunk      <- fiberGraph.dump(a1.id, 1).runCollect
            chunkAll   <- fiberGraph.dumpAll.runCollect
          } yield assert(chunk)(hasSize(equalTo(4))) &&
            assert(chunkAll)(hasSize(equalTo(8))) &&
            assert(chunk.map(_.fiberId.seqNumber))(
              hasSameElements(Chunk(a1.id.seqNumber, a2.id.seqNumber, a3.id.seqNumber, a4.id.seqNumber))
            )
        },
        testM("dump up to level 2") {
          for {
            g          <- createGraph
            fiberGraph <- Ref.make(g).map(graphRef => FiberGraph.apply(graphRef))
            a1          = g.nodes.find(_.label == "a1").get.node
            a2          = g.nodes.find(_.label == "a2").get.node
            a3          = g.nodes.find(_.label == "a3").get.node
            a4          = g.nodes.find(_.label == "a4").get.node
            a5          = g.nodes.find(_.label == "a5").get.node
            a6          = g.nodes.find(_.label == "a6").get.node
            chunk      <- fiberGraph.dump(a1.id, 2).runCollect
          } yield assert(chunk)(hasSize(equalTo(6))) &&
            assert(chunk.map(_.fiberId.seqNumber))(
              hasSameElementsDistinct(
                Chunk(a1.id.seqNumber, a2.id.seqNumber, a3.id.seqNumber, a4.id.seqNumber, a5.id.seqNumber)
              )
            )
        },
        testM("dump up to level 3") {
          for {
            g          <- createGraph
            fiberGraph <- Ref.make(g).map(graphRef => FiberGraph.apply(graphRef))
            a1          = g.nodes.find(_.label == "a1").get.node
            a2          = g.nodes.find(_.label == "a2").get.node
            a3          = g.nodes.find(_.label == "a3").get.node
            a4          = g.nodes.find(_.label == "a4").get.node
            a5          = g.nodes.find(_.label == "a5").get.node
            a6          = g.nodes.find(_.label == "a6").get.node
            a7          = g.nodes.find(_.label == "a7").get.node
            a8          = g.nodes.find(_.label == "a8").get.node
            chunkThree <- fiberGraph.dump(a1.id, 3).runCollect
            chunkTen   <- fiberGraph.dump(a1.id, 10).runCollect
          } yield assert(chunkThree.map(_.fiberId.seqNumber))(equalTo(chunkTen.map(_.fiberId.seqNumber))) &&
            assert(chunkThree.map(_.fiberId.seqNumber))(not(contains(a7.id.seqNumber))) &&
            assert(chunkThree.map(_.fiberId.seqNumber))(not(contains(a8.id.seqNumber))) &&
            assert(chunkThree.size)(equalTo(7)) &&
            assert(chunkTen.size)(equalTo(7))
        },
        testM("dump everything has size of all nodes") {
          for {
            g            <- createGraph
            fiberGraph   <- Ref.make(g).map(graphRef => FiberGraph.apply(graphRef))
            chunkResults <- fiberGraph.dumpAll.runCollect
          } yield assert(chunkResults)(hasSize(equalTo(8)))
        }
      )
    )

  val createGraph: URIO[zio.clock.Clock with console.Console, Graph[Fiber.Runtime[Any, Any], String, String]] = for {
    f1   <- ZIO.effectTotal(2).forever.fork
    f2   <- ZIO.succeed("hello").fork
    f3   <- ZIO.sleep(1.minute).fork
    f4   <- ZIO.never.fork
    f5   <- ZIO.never.fork
    f6   <- ZIO.effectTotal(2).forever.fork
    f7   <- ZIO.effectTotal(6).forever.fork
    f8   <- ZIO.effectTotal(1).forever.fork
    n1    = Node[Fiber.Runtime[Any, Any], String](f1, "a1")
    n2    = Node[Fiber.Runtime[Any, Any], String](f2, "a2")
    n3    = Node[Fiber.Runtime[Any, Any], String](f3, "a3")
    n4    = Node[Fiber.Runtime[Any, Any], String](f4, "a4")
    n5    = Node[Fiber.Runtime[Any, Any], String](f5, "a5")
    n6    = Node[Fiber.Runtime[Any, Any], String](f6, "a6")
    n7    = Node[Fiber.Runtime[Any, Any], String](f7, "a7")
    n8    = Node[Fiber.Runtime[Any, Any], String](f8, "a8")
    nodes = List(n1, n2, n3, n4, n5, n6, n7, n8)
    edges = List(
              Edge[Fiber.Runtime[Any, Any], String](f1, f2, "first"),
              Edge[Fiber.Runtime[Any, Any], String](f1, f3, "first2"),
              Edge[Fiber.Runtime[Any, Any], String](f1, f4, "first3"),
              Edge[Fiber.Runtime[Any, Any], String](f4, f3, "second"),
              Edge[Fiber.Runtime[Any, Any], String](f4, f5, "second2"),
              Edge[Fiber.Runtime[Any, Any], String](f5, f6, "third"),
              Edge[Fiber.Runtime[Any, Any], String](f7, f8, "out Of graph")
            )
  } yield Graph.mkGraph[Fiber.Runtime[Any, Any], String, String](nodes, edges)
}
