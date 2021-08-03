package zio.zmx.client.frontend

import zio.Chunk

import org.scalajs.dom
import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.webtable.WebTable
import AppDataModel.MetricSummary._

object AppViews {

  private val buttonWidth: String = "w-60"

  val summaries: HtmlElement = div(
    counterInfoView.render,
    gaugeInfoView.render,
    histogramInfoView.render,
    summaryInfoView.render,
    setInfoView.render
  )

  val diagrams: HtmlElement =
    div(
      div(
        cls := "bg-gray-900 text-gray-50 rounded p-3 my-3",
        span(
          cls := "text-3xl font-bold my-2",
          "Diagrams"
        )
      ),
      children <-- AppState.diagrams.signal.map(c => c.map(_.render()))
    )

  private def diagramLink(k: String, f: String => Unit): HtmlElement = {
    val handler = Observer[dom.MouseEvent](onNext = _ => f(k))
    a(
      href("#"),
      s"Add diagram",
      cls := "bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 px-4 rounded text-center place-self-center",
      onClick --> handler
    )
  }

  private lazy val counterInfoView =
    WebTable.create[String, CounterInfo](
      cols = Chunk(
        WebTable.ColumnConfig[CounterInfo](
          width = buttonWidth,
          renderer = ci => diagramLink(ci.longName, AppState.addCounterDiagram)
        ),
        WebTable.ColumnConfig[CounterInfo](
          "Name",
          renderer = ci => span(ci.name)
        ),
        WebTable.ColumnConfig[CounterInfo](
          "Labels",
          renderer = ci => span(ci.labels)
        ),
        WebTable.ColumnConfig[CounterInfo](
          "Current",
          renderer = ci => span(f"${ci.current.longValue()}%,d")
        )
      ),
      rk = _.longName
    )(AppState.counterInfo)

  private lazy val gaugeInfoView =
    WebTable.create[String, GaugeInfo](
      cols = Chunk(
        WebTable.ColumnConfig[GaugeInfo](
          width = buttonWidth,
          renderer = gi => diagramLink(gi.longName, AppState.addGaugeDiagram)
        ),
        WebTable.ColumnConfig[GaugeInfo](
          "Name",
          renderer = gi => span(gi.name)
        ),
        WebTable.ColumnConfig[GaugeInfo](
          "Labels",
          renderer = gi => span(gi.labels)
        ),
        WebTable.ColumnConfig[GaugeInfo](
          "Current",
          renderer = gi => span(f"${gi.current}%,8.3f")
        )
      ),
      rk = _.longName
    )(AppState.gaugeInfo)

  private lazy val histogramInfoView =
    WebTable.create[String, HistogramInfo](
      cols = Chunk(
        WebTable.ColumnConfig[HistogramInfo](
          width = buttonWidth,
          renderer = hi => diagramLink(hi.longName, AppState.addHistogramDiagram)
        ),
        WebTable.ColumnConfig[HistogramInfo](
          "Name",
          renderer = hi => span(hi.name)
        ),
        WebTable.ColumnConfig[HistogramInfo](
          "Labels",
          renderer = hi => span(hi.labels)
        ),
        WebTable.ColumnConfig[HistogramInfo](
          "Buckets",
          renderer = hi => span(f"${hi.buckets}%,d")
        ),
        WebTable.ColumnConfig[HistogramInfo](
          "Count",
          renderer = hi => span(f"${hi.count}%,d")
        ),
        WebTable.ColumnConfig[HistogramInfo](
          "Average",
          renderer = hi => span(f"${hi.sum / hi.count}%,8.3f")
        )
      ),
      rk = _.longName
    )(AppState.histogramInfo)

  private lazy val summaryInfoView = WebTable.create[String, SummaryInfo](
    cols = Chunk(
      WebTable.ColumnConfig[SummaryInfo](
        width = buttonWidth,
        renderer = si => diagramLink(si.longName, AppState.addSummaryDiagram)
      ),
      WebTable.ColumnConfig[SummaryInfo](
        "Name",
        renderer = si => span(si.name)
      ),
      WebTable.ColumnConfig[SummaryInfo](
        "Labels",
        renderer = si => span(si.labels)
      ),
      WebTable.ColumnConfig[SummaryInfo](
        "Quantiles",
        renderer = si => span(f"${si.quantiles}%,d")
      ),
      WebTable.ColumnConfig[SummaryInfo](
        "Count",
        renderer = si => span(f"${si.count}%,d")
      ),
      WebTable.ColumnConfig[SummaryInfo](
        "Average",
        renderer = si => span(f"${si.sum / si.count}%,8.3f")
      )
    ),
    rk = _.longName
  )(AppState.summaryInfo)

  private lazy val setInfoView = WebTable.create[String, SetInfo](
    cols = Chunk(
      WebTable.ColumnConfig[SetInfo](
        width = buttonWidth,
        renderer = si => diagramLink(si.longName, AppState.addSetDiagram)
      ),
      WebTable.ColumnConfig[SetInfo](
        "Name",
        renderer = si => span(si.name)
      ),
      WebTable.ColumnConfig[SetInfo](
        "Labels",
        renderer = si => span(si.labels)
      ),
      WebTable.ColumnConfig[SetInfo](
        "Tokens",
        renderer = si => span(f"${si.keys}%,d")
      ),
      WebTable.ColumnConfig[SetInfo](
        "Count",
        renderer = si => span(f"${si.count}%,d")
      )
    ),
    rk = _.longName
  )(AppState.setInfo)
}
