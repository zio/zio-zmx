package zio.zmx.client.frontend.icons

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveSvgElement

object HeroIcon {

  sealed abstract class SolidIcon(private val path: String)

  object SolidIcon {

    implicit class SolidIconSyntax(self: SolidIcon) {
      def apply(
        mods: Modifier[ReactiveSvgElement[org.scalajs.dom.svg.SVG]]*
      ): ReactiveSvgElement[org.scalajs.dom.svg.SVG] = svg.svg(
        mods,
        svg.strokeWidth := "2",
        svg.viewBox := "0 0 20 20",
        svg.path(
          svg.stroke := "currentColor",
          svg.fillRule := "evenOdd",
          svg.clipRule := "evenOdd",
          svg.d := self.path
        )
      )
    }

    val arrowUp: SolidIcon   = new SolidIcon(
      "M3.293 9.707a1 1 0 010-1.414l6-6a1 1 0 011.414 0l6 6a1 1 0 01-1.414 1.414L11 5.414V17a1 1 0 11-2 0V5.414L4.707 9.707a1 1 0 01-1.414 0z"
    ) {}

    val arrowDown: SolidIcon = new SolidIcon(
      "M16.707 10.293a1 1 0 010 1.414l-6 6a1 1 0 01-1.414 0l-6-6a1 1 0 111.414-1.414L9 14.586V3a1 1 0 012 0v11.586l4.293-4.293a1 1 0 011.414 0z"
    ) {}

    val close: SolidIcon     = new SolidIcon(
      "M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
    ) {}

    val locked: SolidIcon    = new SolidIcon(
      "M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z"
    ) {}
  }
}
