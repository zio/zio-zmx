package zio.zmx.diagnostics.graph

import scala.annotation.tailrec

import com.github.ghik.silencer.silent
import Graph._

case class Graph[N, A, B](repr: Map[N, Context[N, A, B]]) {

  def &(c: Context[N, A, B]): Graph[N, A, B] = {
    val Context(_, v, _, _) = c
    if (repr contains v)
      throw new IllegalArgumentException("node already exists")
    else {
      val Context(p, v, _, s) = c
      val g1                  = repr + (v -> c)
      val g2                  = updateAdjacencies(g1, p, addSuccessor(v, _, _))
      val g3                  = updateAdjacencies(g2, s, addPredecessor(v, _, _))
      Graph(g3)
    }
  }

  def decompose(v: N): Decomposition[N, A, B] =
    repr get v match {
      case None                      =>
        Decomposition(None, this)
      case Some(Context(p, _, l, s)) =>
        val s1 = s filter (_._2 != v)
        val p1 = p filter (_._2 != v)
        val g1 = repr - v
        val g2 = updateAdjacencies(g1, s1, clearPredecessors(v, _, _))
        val g3 = updateAdjacencies(g2, p1, clearSuccessors(v, _, _))
        Decomposition(Some(Context(p1, v, l, s)), Graph(g3))
    }

  @silent
  def decompose: GraphDecomposition[N, A, B] =
    if (repr.isEmpty)
      throw new NoSuchElementException("decompose on empty graph")
    else {
      val Decomposition(Some(c), g) = decompose(repr.keys.head)
      GraphDecomposition(c, g)
    }

  def isEmpty: Boolean =
    this match {
      case Empty() => true
      case _       => false
    }

  def addNode(v: Node[N, A]): Graph[N, A, B] =
    decompose(v.node) match {
      case Decomposition(Some(Context(p, _, _, s)), g) =>
        Context(p, v.node, v.label, s) & g
      case Decomposition(_, g)                         =>
        Context[N, A, B](Set.empty, v.node, v.label, Set.empty) & g
    }

  def addNodes(vs: Iterable[Node[N, A]]): Graph[N, A, B] =
    vs.foldLeft(this)(_ addNode _)

  def addNodes(v1: Node[N, A], v2: Node[N, A], vs: Node[N, A]*): Graph[N, A, B] =
    this addNode v1 addNode v2 addNodes vs

  def addEdge(e: Edge[N, B]): Graph[N, A, B] = {
    val Edge(v, w, l) = e
    decompose(v) match {
      case Decomposition(Some(c), g) => c.addSuccessor(w, l) & g
      case _                         => throw new NoSuchElementException("node not found: " + v)
    }
  }

  def addEdges(es: Iterable[Edge[N, B]]): Graph[N, A, B] =
    es.foldLeft(this)(_ addEdge _)

  def addEdges(e1: Edge[N, B], e2: Edge[N, B], es: Edge[N, B]*): Graph[N, A, B] =
    this addEdge e1 addEdge e2 addEdges es

  def union(that: Graph[N, A, B]): Graph[N, A, B] =
    this addNodes that.nodes addEdges that.edges

  def removeNode(v: N): Graph[N, A, B] =
    decompose(v) match {
      case Decomposition(_, g) => g
    }

  def removeNodes(vs: Iterable[N]): Graph[N, A, B] =
    vs.foldLeft(this)(_ removeNode _)

  def removeNodes(v1: N, v2: N, vs: N*): Graph[N, A, B] =
    this removeNode v1 removeNode v2 removeNodes vs

  def removeEdge(e: (N, N)): Graph[N, A, B] = {
    val (v, w) = e
    decompose(v) match {
      case Decomposition(Some(c), g) => c.removeSuccessor(w) & g
      case _                         => throw new NoSuchElementException("node not found: " + v)
    }
  }

  def removeEdges(es: Iterable[(N, N)]): Graph[N, A, B] =
    es.foldLeft(this)(_ removeEdge _)

  def removeEdges(e1: (N, N), e2: (N, N), es: (N, N)*): Graph[N, A, B] =
    this removeEdge e1 removeEdge e2 removeEdges es

  def updateNode(v: Node[N, A]): Graph[N, A, B] =
    decompose(v.node) match {
      case Decomposition(Some(Context(p, _, _, s)), g) => Context(p, v.node, v.label, s) & g
      case _                                           => throw new NoSuchElementException("node not found: " + v)
    }

  def updateNodes(vs: Iterable[Node[N, A]]): Graph[N, A, B] =
    vs.foldLeft(this)(_ updateNode _)

  def updateEdge(e: Edge[N, B]): Graph[N, A, B] = {
    val Edge(v, w, _) = e
    removeEdge((v, w)).addEdge(e)
  }

  def updateEdges(es: Iterable[Edge[N, B]]): Graph[N, A, B] =
    es.foldLeft(this)(_ updateEdge _)

  def contains(v: N): Boolean =
    decompose(v) match {
      case Decomposition(Some(_), _) => true
      case _                         => false
    }

  def context(v: N): Context[N, A, B] =
    decompose(v) match {
      case Decomposition(None, _)    => throw new NoSuchElementException("node not found: " + v)
      case Decomposition(Some(c), _) => c
    }

  def node(v: N): Option[Node[N, A]] =
    decompose(v).context.map { case Context(_, _, l, _) => Node(v, l) }

  def edge(e: (N, N)): Option[Edge[N, B]] = {
    val (v, w) = e
    decompose(v).context.flatMap(_.outEdges.find(_.to == w))
  }

  def successors(v: N): Set[N] =
    context(v).successors

  def predecessors(v: N): Set[N] =
    context(v).predecessors

  def neighbors(v: N): Set[N] =
    context(v).neighbors

  def outEdges(v: N): Set[Edge[N, B]] =
    context(v).outEdges

  def inEdges(v: N): Set[Edge[N, B]] =
    context(v).inEdges

  def outDegree(v: N): Int =
    context(v).outDegree

  def inDegree(v: N): Int =
    context(v).inDegree

  def degree(v: N): Int =
    context(v).degree

  def fold[C](z: C)(op: (Context[N, A, B], C) => C): C =
    this match {
      case Empty() => z
      case c & g   => op(c, g.fold(z)(op))
    }

  def map[C, D](f: Context[N, A, B] => Context[N, C, D]): Graph[N, C, D] =
    fold(empty[N, C, D])(f(_) & _)

  def mapNodes[C](f: A => C): Graph[N, C, B] =
    map { case Context(p, v, l, s) => Context(p, v, f(l), s) }

  def mapEdges[C](f: B => C): Graph[N, A, C] = {
    def mapAdjacencies(adj: Set[(B, N)])(g: B => C): Set[(C, N)] =
      adj map { case (l, v) => (g(l), v) }
    map { case Context(p, v, l, s) => Context(mapAdjacencies(p)(f), v, l, mapAdjacencies(s)(f)) }
  }

  @silent
  def extend[C](f: GraphDecomposition[N, A, B] => C): Graph[N, C, B] = {
    val self = this
    map {
      case Context(p, v, _, s) =>
        val Decomposition(Some(c), g) = self decompose v
        Context(p, v, f(GraphDecomposition(c, g)), s)
    }
  }

  def nodes: Set[Node[N, A]] =
    fold(Set.empty[Node[N, A]]) { case (Context(_, v, l, _), vs) => vs + Node(v, l) }

  def edges: Set[Edge[N, B]] =
    fold(Set.empty[Edge[N, B]]) {
      case (Context(p, v, _, s), es) =>
        val ps = p.map { case (l, w) => Edge(w, v, l) } ++ s.map { case (l, w) => Edge(v, w, l) }
        ps ++ es
    }

  def contexts: Set[Context[N, A, B]] =
    nodes map { case Node(v, _) => context(v) }

  def order: Int                      =
    nodes.size

  def size: Int =
    edges.size

  def filterNodes(p: Node[N, A] => Boolean): Graph[N, A, B] =
    removeNodes(nodes.filter(!p(_)).map(_.node))

  def filterEdges(p: Edge[N, B] => Boolean): Graph[N, A, B] =
    removeEdges(edges.filter(!p(_)).map(e => (e.from, e.to)))

  def reverse: Graph[N, A, B]    =
    map { case Context(p, v, l, s) => Context(s, v, l, p) }

  def undirected: Graph[N, A, B] =
    map {
      case Context(p, v, l, s) =>
        val ps = p ++ s
        Context(ps, v, l, ps)
    }

  def unlabel: Graph[N, Unit, Unit] = {
    def unlabelAdjacencies(adj: Set[(B, N)]): Set[(Unit, N)] =
      adj map { case (_, v) => ((), v) }
    map { case Context(p, v, _, s) => Context(unlabelAdjacencies(p), v, (), unlabelAdjacencies(s)) }
  }

  @tailrec
  private def updateAdjacencies(
    g: Map[N, Context[N, A, B]],
    adj: Set[(B, N)],
    f: (B, Context[N, A, B]) => Context[N, A, B]
  ): Map[N, Context[N, A, B]] =
    adj.headOption match {
      case Some((l, v)) =>
        g get v match {
          case Some(context) => updateAdjacencies(g + (v -> f(l, context)), adj.tail, f)
          case None          => throw new NoSuchElementException("node not found: " + v)
        }
      case None         => g
    }

  private def addSuccessor(v: N, l: B, c: Context[N, A, B]): Context[N, A, B] = {
    val Context(p, v1, l1, s) = c
    Context(p, v1, l1, s + (l -> v))
  }

  private def addPredecessor(v: N, l: B, c: Context[N, A, B]): Context[N, A, B] = {
    val Context(p, v1, l1, s) = c
    Context(p + (l -> v), v1, l1, s)
  }

  @silent
  private def clearSuccessors(v: N, l: B, c: Context[N, A, B]): Context[N, A, B] = {
    val Context(p, v1, l1, s) = c
    Context(p, v1, l1, s filter (_._2 != v))
  }

  @silent
  private def clearPredecessors(v: N, l: B, c: Context[N, A, B]): Context[N, A, B] = {
    val Context(p, v1, l1, s) = c
    Context(p filter (_._2 != v), v1, l1, s)
  }

  override def toString =
    contexts map {
      case Context(_, v, l, s) =>
        v.toString + ":" + l.toString + "->" + s.mkString("[", ",", "]")
    } mkString "\n"
}

case class Context[N, A, B](inAdjacencies: Set[(B, N)], node: N, label: A, outAdjacencies: Set[(B, N)]) {

  def &(g: Graph[N, A, B]): Graph[N, A, B] =
    g & this

  def successors: Set[N] =
    outs map (_._2)

  def predecessors: Set[N] =
    ins map (_._2)

  def neighbors: Set[N] =
    inAdjacencies.map(_._2) ++ outAdjacencies.map(_._2)

  def outEdges: Set[Edge[N, B]] =
    outs map { case (l, w) => Edge(node, w, l) }

  def inEdges: Set[Edge[N, B]]  =
    ins map { case (l, w) => Edge(w, node, l) }

  def outDegree: Int            =
    outs.size

  def inDegree: Int =
    ins.size

  def degree: Int =
    inAdjacencies.size + outAdjacencies.size

  def addSuccessor(v: N, l: B): Context[N, A, B]   =
    Context(inAdjacencies, node, label, outAdjacencies + (l -> v))

  def addPredecessor(v: N, l: B): Context[N, A, B] =
    Context(inAdjacencies + (l -> v), node, label, outAdjacencies)

  def removeSuccessor(v: N): Context[N, A, B]      =
    Context(inAdjacencies, node, label, outAdjacencies filter (_._2 != v))

  def removePredecessor(v: N): Context[N, A, B] =
    Context(inAdjacencies filter (_._2 != v), node, label, outAdjacencies)

  private def ins: Set[(B, N)] =
    inAdjacencies ++ outAdjacencies.filter(_._2 == node)

  private def outs: Set[(B, N)] =
    outAdjacencies ++ inAdjacencies.filter(_._2 == node)
}

case class Decomposition[N, A, B](context: Option[Context[N, A, B]], rest: Graph[N, A, B])

case class GraphDecomposition[N, A, B](context: Context[N, A, B], rest: Graph[N, A, B]) {

  def node: N =
    context.node

  def map[C](f: A => C): GraphDecomposition[N, C, B] = {
    val Context(p, v, l, s) = context
    GraphDecomposition(Context(p, v, f(l), s), rest mapNodes f)
  }

  def extract: A =
    context.label

  @silent
  def extend[C](f: GraphDecomposition[N, A, B] => C): GraphDecomposition[N, C, B] = {
    val Decomposition(Some(c), g) = toGraph extend f decompose node
    GraphDecomposition(c, g)
  }

  def toGraph: Graph[N, A, B] =
    context & rest
}

case class Node[N, A](node: N, label: A)

case class Edge[N, B](from: N, to: N, label: B)

object Graph {

  object Empty {
    def unapply[N, A, B](g: Graph[N, A, B]): Boolean =
      g.repr.isEmpty
  }

  object & {
    def unapply[N, A, B](g: Graph[N, A, B]): Option[(Context[N, A, B], Graph[N, A, B])] =
      if (g.repr.isEmpty)
        None
      else {
        val GraphDecomposition(c, g1) = g.decompose
        Some((c, g1))
      }
  }

  def empty[N, A, B]: Graph[N, A, B] = Graph(Map.empty[N, Context[N, A, B]])

  def mkGraph[N, A, B](vs: Iterable[Node[N, A]], es: Iterable[Edge[N, B]]): Graph[N, A, B] =
    empty addNodes vs addEdges es
}
