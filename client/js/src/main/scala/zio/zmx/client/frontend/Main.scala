package zio.zmx.client.frontend

import com.raquo.laminar.api.L._
import org.scalajs.dom

object Main {

  def main(args: Array[String]): Unit = {

    val _ = documentEvents.onDomContentLoaded.foreach { _ =>
      val appContainer = dom.document.querySelector("#app")
      appContainer.innerHTML = ""
      val _            = render(appContainer, view)
    }(unsafeWindowOwner)
  }

  def view: Div =
    div(
      "ZMX ScalaJS client",
      AppViews.summaries,
      AppViews.diagrams,
      AppState.initWs()
    )
}
