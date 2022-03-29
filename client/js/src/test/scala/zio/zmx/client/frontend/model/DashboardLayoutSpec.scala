package zio.zmx.client.frontend.model

import zio._
import zio.test._
import zio.test.TestAspect._

import Layout._

object DashboardLayoutSpec extends DefaultRunnableSpec {

  type IntDashBoard = Dashboard[Int]

  val cell: Int => IntDashBoard = Dashboard.Cell(_)
  val empty                     = Dashboard.Empty

  override def spec = suite("The DashboardLayout should")(
    simpleCombineH,
    simpleCombineV,
    simpleOptimizeH,
    simpleOptimizeV,
    simpleReduce,
    removeEmpty,
    optimiseNested,
  ) @@ timed @@ timeout(60.seconds)

  private val simpleCombineH = test("combine cells horizontally") {
    val c1: IntDashBoard = cell(1)
    val c2: IntDashBoard = cell(2)

    val d: IntDashBoard = c1 || c2

    assertTrue(d.equals(Dashboard.HGroup(Chunk(c1, c2))))
  }

  private val simpleCombineV = test("combine cells vertically") {
    val c1: IntDashBoard = cell(1)
    val c2: IntDashBoard = cell(2)

    val d: IntDashBoard = c1 ^^ c2

    assertTrue(d.equals(Dashboard.VGroup(Chunk(c1, c2))))
  }

  private val simpleOptimizeH = test("combine 2 HGroups into one") {
    val c1 = cell(1)
    val c2 = cell(2)

    val grp = Dashboard.HGroup(
      Chunk(
        Dashboard.HGroup(Chunk(c1)),
        Dashboard.HGroup(Chunk(c2)),
      ),
    )

    assertTrue(grp.optimize.equals(Dashboard.HGroup(Chunk(c1, c2))))
  }

  private val simpleOptimizeV = test("combine 2 VGroups into one") {
    val c1 = cell(1)
    val c2 = cell(2)

    val grp = Dashboard.VGroup(
      Chunk(
        Dashboard.VGroup(Chunk(c1)),
        Dashboard.VGroup(Chunk(c2)),
      ),
    )

    assertTrue(grp.optimize.equals(Dashboard.VGroup(Chunk(c1, c2))))
  }

  private val simpleReduce = test("reduce simple cells") {
    assertTrue(empty.optimize.equals(empty)) &&
    assertTrue(cell(1).optimize.equals(cell(1)))
  }

  private val removeEmpty = test("remove empty cells") {
    assertTrue(Dashboard.HGroup(Chunk(cell(1), empty, empty, empty)).optimize.equals(cell(1))) &&
    assertTrue(
      Dashboard.VGroup(Chunk(cell(1), empty, empty, cell(2))).optimize.equals(Dashboard.VGroup(Chunk(cell(1), cell(2)))),
    )
  }

  private val optimiseNested = test("optimise a nested dashboard") {

    val sub1  = Dashboard.VGroup(Chunk(cell(1)))          // should optimize to cell(1)
    val sub1a = Dashboard.VGroup(Chunk(cell(4), cell(5))) // should remain untouched
    val sub2  = Dashboard.Empty                           // should just be removed
    val sub3  = Dashboard.HGroup(Chunk(cell(2), cell(3)))

    val db1 = Dashboard.HGroup(Chunk(sub1, sub2, sub3))  // should optimise to HGroup(1,2,3)
    val db2 = Dashboard.HGroup(Chunk(sub1a, sub2, sub3)) // should optimise to HGroup(VGroup(4,5),2,3)

    assertTrue(db1.optimize.equals(Dashboard.HGroup(Chunk(cell(1), cell(2), cell(3))))) &&
    assertTrue(db2.optimize.equals(Dashboard.HGroup(Chunk(sub1a, cell(2), cell(3)))))
  }
}
