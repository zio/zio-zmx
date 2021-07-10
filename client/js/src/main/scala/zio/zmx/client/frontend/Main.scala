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
      cls := "flex",
      div(
        cls := "p-6 w-full",
        div(
          cls := "p-3 w-full bg-gray-900 text-gray-50 rounded",
          span(
            cls := "text-4xl font-bold",
            "ZIO ZMX ScalaJS Client"
          )
        ),
        AppViews.summaries,
        AppViews.diagrams,
        AppState.initWs()
      )
    )
}
