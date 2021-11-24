package zio.zmx.client.frontend.d3v7

import scalajs.js
import scala.scalajs.js.annotation._

import org.scalajs.dom.CanvasRenderingContext2D

// https://github.com/d3/d3-shape
@JSImport("d3-shape", JSImport.Namespace)
@js.native
object d3Shape extends js.Object {
  def pie(): PieGenerator                           = js.native
  def arc(): ArcGenerator                           = js.native
  def line(): LineGenerator                         = js.native
  def curveBasisClosed: CurveFactory                = js.native
  def curveCardinalClosed: CurveFactory             = js.native
  def curveCatmullRom: CurveCatmullRomFactory       = js.native
  def curveCatmullRomClosed: CurveCatmullRomFactory = js.native
  def curveLinear: CurveFactory                     = js.native
  def curveLinearClosed: CurveFactory               = js.native

  @js.native
  trait BaseGenerator[G <: BaseGenerator[G]] extends js.Object {
    def context(context: CanvasRenderingContext2D): G = js.native
    def context(): CanvasRenderingContext2D           = js.native
  }

  @js.native
  trait BaseLineGenerator[G <: BaseLineGenerator[G]] extends js.Object with BaseGenerator[G] {
    def curve(curve: CurveFactory): G = js.native
  }

  @js.native
  trait LineGenerator extends BaseLineGenerator[LineGenerator] {
    def apply(data: js.Array[js.Tuple2[Double, Double]]): String = js.native
  }

  @js.native
  trait CurveFactory extends js.Object

  @js.native
  trait CurveCatmullRomFactory extends CurveFactory {
    def alpha(alpha: Double): CurveCatmullRomFactory = js.native
  }

  @js.native
  trait PieGenerator extends js.Object {
    def value(value: Double): PieGenerator                                = js.native
    def padAngle(angle: Double): PieGenerator                             = js.native
    def apply[Datum](data: js.Array[Datum]): js.Array[PieArcDatum[Datum]] = js.native
  }

  @js.native
  trait PieArcDatum[Datum] extends ArcDatum {
    def data: Datum        = js.native
    def value: Double      = js.native
    def index: Int         = js.native
    def startAngle: Double = js.native
    def endAngle: Double   = js.native
    def padAngle: Double   = js.native
  }

  @js.native
  trait ArcDatum extends js.Object

  @js.native
  trait BaseArcGenerator[G <: BaseArcGenerator[G]] extends js.Object with BaseGenerator[G] {
    def innerRadius(radius: Double): G  = js.native
    def outerRadius(radius: Double): G  = js.native
    def cornerRadius(radius: Double): G = js.native
  }

  @js.native
  trait ArcGenerator extends BaseArcGenerator[ArcGenerator] {
    def apply[T <: ArcDatum](arguments: T): String                       = js.native
    def centroid[T <: ArcDatum](arguments: T): js.Tuple2[Double, Double] = js.native
  }

  @js.native
  trait ArcGeneratorWithContext extends BaseArcGenerator[ArcGeneratorWithContext] {
    def apply[T <: ArcDatum](arguments: T): Unit = js.native
  }
}
