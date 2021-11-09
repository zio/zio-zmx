package zio.zmx.client.frontend.icons

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveSvgElement

object HeroIcon {

  sealed trait SolidIcon {
    def path: String
  }

  object SolidIcon {

    implicit class SolidIconSyntax(self: SolidIcon) {
      def apply(
        mods: Modifier[ReactiveSvgElement[org.scalajs.dom.svg.SVG]]*
      ): ReactiveSvgElement[org.scalajs.dom.svg.SVG] = svg.svg(
        mods,
        svg.fill := "none",
        svg.stroke := "currentColor",
        svg.strokeWidth := "2",
        svg.path(
          svg.viewBox := "0 0 20 20",
          svg.d := self.path
        )
      )
    }

    final case class ArrowUp private (
      path: String =
        "M3.293 9.707a1 1 0 010-1.414l6-6a1 1 0 011.414 0l6 6a1 1 0 01-1.414 1.414L11 5.414V17a1 1 0 11-2 0V5.414L4.707 9.707a1 1 0 01-1.414 0z"
    ) extends SolidIcon

    final case class ArrowDown private (
      path: String =
        "M16.707 10.293a1 1 0 010 1.414l-6 6a1 1 0 01-1.414 0l-6-6a1 1 0 111.414-1.414L9 14.586V3a1 1 0 012 0v11.586l4.293-4.293a1 1 0 011.414 0z"
    ) extends SolidIcon

    final case class Close private (
      path: String =
        "M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
    ) extends SolidIcon

    final case class Locked private (
      path: String =
        "M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z"
    )
  }
}
