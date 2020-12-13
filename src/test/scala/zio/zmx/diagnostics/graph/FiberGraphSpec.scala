package zio.zmx.diagnostics.graph

import zio.test._
import zio._
import zio.test.Assertion._
import zio.duration._

object FiberGraphSpec extends DefaultRunnableSpec {

  def spec =
    suite("FiberGraph tests")(
      testM("dump specific fiber") {
        for {
          g              <- createGraph
          fiberGraph     <- Ref.make(g).map(graphRef => FiberGraph.apply(graphRef))
          fiber1          = g.nodes.find(_.label == "f1").get.node
          fiberGraphDump <- fiberGraph.dump(fiber1.id)
          directDump     <- fiber1.dump
        } yield assert(fiberGraphDump.fiberId.seqNumber)(equalTo(directDump.fiberId.seqNumber))
      },
      testM("dump up to level of 0 && dump(id) == dump(id, 0) ") {
        for {
          g           <- createGraph
          fiberGraph  <- Ref.make(g).map(graphRef => FiberGraph.apply(graphRef))
          f1           = g.nodes.find(_.label == "f1").get.node
          dumpFiber1  <- fiberGraph.dump(f1.id)
          fiber1Chunk <- fiberGraph.dump(f1.id, 0).runCollect
        } yield assert(fiber1Chunk)(hasSize(equalTo(1))) &&
          assert(fiber1Chunk.head.fiberId.seqNumber)(equalTo(dumpFiber1.fiberId.seqNumber))
      },
      testM("dump up to level of 1") {
        for {
          g          <- createGraph
          fiberGraph <- Ref.make(g).map(graphRef => FiberGraph.apply(graphRef))
          f1          = g.nodes.find(_.label == "f1").get.node
          f2          = g.nodes.find(_.label == "f2").get.node
          f3          = g.nodes.find(_.label == "f3").get.node
          f4          = g.nodes.find(_.label == "f4").get.node
          chunk      <- fiberGraph.dump(f1.id, 1).runCollect
        } yield assert(chunk)(hasSize(equalTo(4))) &&
          assert(chunk.map(_.fiberId.seqNumber))(
            hasSameElements(Chunk(f1.id.seqNumber, f2.id.seqNumber, f3.id.seqNumber, f4.id.seqNumber))
          )
      },
      testM("dump up to level of 2") {
        for {
          g          <- createGraph
          fiberGraph <- Ref.make(g).map(graphRef => FiberGraph.apply(graphRef))
          f1          = g.nodes.find(_.label == "f1").get.node
          f2          = g.nodes.find(_.label == "f2").get.node
          f3          = g.nodes.find(_.label == "f3").get.node
          f4          = g.nodes.find(_.label == "f4").get.node
          f5          = g.nodes.find(_.label == "f5").get.node
          chunk      <- fiberGraph.dump(f1.id, 2).runCollect
        } yield assert(chunk)(hasSize(equalTo(5))) &&
          assert(chunk.map(_.fiberId.seqNumber))(
            hasSameElementsDistinct(
              Chunk(f1.id.seqNumber, f2.id.seqNumber, f3.id.seqNumber, f4.id.seqNumber, f5.id.seqNumber)
            )
          )
      },
      testM("dump up to level of 3") {
        for {
          g          <- createGraph
          fiberGraph <- Ref.make(g).map(graphRef => FiberGraph.apply(graphRef))
          f1          = g.nodes.find(_.label == "f1").get.node
          f7          = g.nodes.find(_.label == "f7").get.node
          f8          = g.nodes.find(_.label == "f8").get.node
          chunkThree <- fiberGraph.dump(f1.id, 3).runCollect
          chunkTen   <- fiberGraph.dump(f1.id, 10).runCollect
        } yield assert(chunkThree.map(_.fiberId.seqNumber))(equalTo(chunkTen.map(_.fiberId.seqNumber))) &&
          assert(chunkThree.map(_.fiberId.seqNumber))(not(contains(f7.id.seqNumber))) &&
          assert(chunkThree.map(_.fiberId.seqNumber))(not(contains(f8.id.seqNumber))) &&
          assert(chunkThree.size)(equalTo(6)) &&
          assert(chunkTen.size)(equalTo(6))
      },
      testM("dump all") {
        for {
          g            <- createGraph
          fiberGraph   <- Ref.make(g).map(graphRef => FiberGraph.apply(graphRef))
          chunkResults <- fiberGraph.dumpAll.runCollect
        } yield assert(chunkResults)(hasSize(equalTo(8)))
      }
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
    nodes = List(
              Node[Fiber.Runtime[Any, Any], String](f1, "f1"),
              Node[Fiber.Runtime[Any, Any], String](f2, "f2"),
              Node[Fiber.Runtime[Any, Any], String](f3, "f3"),
              Node[Fiber.Runtime[Any, Any], String](f4, "f4"),
              Node[Fiber.Runtime[Any, Any], String](f5, "f5"),
              Node[Fiber.Runtime[Any, Any], String](f6, "f6"),
              Node[Fiber.Runtime[Any, Any], String](f7, "f7"),
              Node[Fiber.Runtime[Any, Any], String](f8, "f8")
            )
    edges = List(
              Edge[Fiber.Runtime[Any, Any], String](f1, f2, "f1_f2_edge"),
              Edge[Fiber.Runtime[Any, Any], String](f1, f3, "f1_f3_edge"),
              Edge[Fiber.Runtime[Any, Any], String](f1, f4, "f1_f4_edge"),
              Edge[Fiber.Runtime[Any, Any], String](f4, f3, "f4_f3_edge"),
              Edge[Fiber.Runtime[Any, Any], String](f4, f5, "f4_f5_edge"),
              Edge[Fiber.Runtime[Any, Any], String](f5, f6, "f5_f6_edge"),
              Edge[Fiber.Runtime[Any, Any], String](f7, f8, "f7_f8_edge")
            )
  } yield Graph.mkGraph[Fiber.Runtime[Any, Any], String, String](nodes, edges)
}
