package zio.zmx.client.frontend.vega

import scalajs.js
import scalajs.js.JSON
import org.scalajs.dom
import zio.zmx.client.frontend.model._
import java.util.concurrent.atomic.AtomicInteger

class VegaEditorProxy(cfg: PanelConfig.DisplayConfig) {

  private val url                 = "https://vega.github.io/editor/"
  private val vegaSpec            = JSON.stringify(VegaModel(cfg).vegaDef)
  private val step                = 250d
  private val count               = new AtomicInteger(40)
  private var handle: Option[Int] = None

  private val zmxWindow = dom.window
  private val editor    = zmxWindow.open(url)

  private val ackListener = { evt: dom.MessageEvent =>
    if (evt.source == editor) {
      count.set(0)
    }
  }

  private val msg = js.Dynamic.literal(
    "config"   -> js.Dynamic.literal(),
    "renderer" -> "canvas",
    "mode"     -> "vega-lite",
    "spec"     -> vegaSpec
  )

  private val send = () => {
    if (count.get() <= 0) {
      zmxWindow.removeEventListener("message", ackListener, false)
      handle.foreach(zmxWindow.clearInterval)
    } else {
      count.decrementAndGet()
      editor.postMessage(msg, url, js.Array[dom.Transferable]())
    }
  }

  def open(): Unit = {
    zmxWindow.addEventListener[dom.MessageEvent]("message", ackListener, false)
    handle = Some(zmxWindow.setInterval(send, step))
  }
}
