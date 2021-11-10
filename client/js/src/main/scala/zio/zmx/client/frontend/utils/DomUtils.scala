package zio.zmx.client.frontend.utils

import scala.util.matching.Regex

object DomUtils {

  /**
   * Taken from https://github.com/scala-js/scala-js-dom/blob/series%2F1.x/src/main/scala/org/scalajs/dom/ext/Extensions.scala
   * This `Color` class has been dropped since scalajs-dom 2.0.
   *
   * Encapsulates a Color, allowing you to do useful work with it
   * before serializing it to a String
   */
  final case class Color(r: Int, g: Int, b: Int) {
    override def toString: String = s"rgb($r, $g, $b)"

    def toHex: String = f"#$r%02x$g%02x$b%02x"

    def *(c: Color): Color = Color(r * c.r, g * c.g, b * c.b)

    def +(c: Color): Color = Color(r + c.r, g + c.g, b + c.b)
  }

  object Color {

    val d: String       = "[0-9a-zA-Z]"
    val RGB: Regex      = "rgb\\((\\d+), (\\d+), (\\d+)\\)".r
    val ShortHex: Regex = s"#($d)($d)($d)".r
    val LongHex: Regex  = s"#($d$d)($d$d)($d$d)".r

    def hex(x: String): Int = Integer.parseInt(x, 16)

    // TODO: the `match` here is not exhaustive; also, this is technically unsafe so make it safe?
    def apply(s: String): Color =
      s match {
        case RGB(r, g, b)      =>
          Color(r.toInt, g.toInt, b.toInt)
        case ShortHex(r, g, b) =>
          Color(hex(r) * 16, hex(g) * 16, hex(b) * 16)
        case LongHex(r, g, b)  =>
          Color(hex(r), hex(g), hex(b))
      }

    val White: Color   = Color(255, 255, 255)
    val Red: Color     = Color(255, 0, 0)
    val Green: Color   = Color(0, 255, 0)
    val Blue: Color    = Color(0, 0, 255)
    val Cyan: Color    = Color(0, 255, 255)
    val Magenta: Color = Color(255, 0, 255)
    val Yellow: Color  = Color(255, 255, 0)
    val Black: Color   = Color(0, 0, 0)

    val all = Seq(
      White,
      Red,
      Green,
      Blue,
      Cyan,
      Magenta,
      Yellow,
      Black
    )
  }

}
