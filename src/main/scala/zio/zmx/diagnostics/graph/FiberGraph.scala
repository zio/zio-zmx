package zio.zmx.diagnostics.graph

import zio.stream._
import zio._

trait FiberGraph {
  def dumpAll: ZStream[Any, Nothing, Fiber.Dump]
  def dump(id: Fiber.Id, depth: Int): ZStream[Any, Nothing, Fiber.Dump]
  def dump(id: Fiber.Id): ZIO[Any, Nothing, Fiber.Dump]
}

object FiberGraph {

  def apply(gRef: Ref[Graph[Fiber.Runtime[Any, Any], String, String]]): FiberGraph = new FiberGraph {

    private def findNode(id: Fiber.Id): UIO[Fiber.Runtime[Any, Any]] =
      gRef.get.map(_.nodes.find(_.node.id == id) match {
        case None        => throw new NoSuchElementException(s"Node with fiber id $id does not exist")
        case Some(value) => value.node
      })

    def dump(id: Fiber.Id): ZIO[Any, Nothing, Fiber.Dump] =
      for {
        n <- findNode(id)
        d <- n.dump
      } yield (d)

    def dump(id: Fiber.Id, depth: Int): ZStream[Any, Nothing, Fiber.Dump] = {

      def traverseChildren(
        fiber: Fiber.Runtime[Any, Any],
        graph: Graph[Fiber.Runtime[Any, Any], String, String],
        i: Int,
        alreadyTraversed: Chunk[Fiber.Runtime[Any, Any]]
      ): ZStream[Any, Nothing, Fiber.Runtime[Any, Any]] =
        if (i == 0)
          ZStream.empty
        else {
          val children = graph.successors(fiber).filter(fiber => !alreadyTraversed.contains(fiber))
          ZStream.fromIterable(children) ++ ZStream
            .fromIterable(children)
            .flatMap(childFiber => traverseChildren(childFiber, graph, i - 1, alreadyTraversed ++ children))
        }

      val nodeAndGraph = for {
        n <- findNode(id)
        g <- gRef.get
      } yield (n, g)

      ZStream
        .fromEffect(nodeAndGraph)
        .flatMap(ng => ZStream(ng._1) ++ traverseChildren(ng._1, ng._2, depth, Chunk(ng._1)))
        .mapM(_.dump)
    }

    def dumpAll: ZStream[Any, Nothing, Fiber.Dump] =
      ZStream
        .fromEffect(gRef.get)
        .flatMap(g => ZStream.fromIterable(g.nodes.map(_.node)))
        .mapM(_.dump)
  }
}
