package zio.zmx.client.frontend.views

import zio._
import zio.metrics._

import org.scalajs.dom
import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.model.MetricSummary._
import zio.zmx.client.frontend.state.AppState
import zio.zmx.client.frontend.utils.Implicits._
import zio.zmx.client.frontend.model.DiagramConfig
import java.util.UUID
import java.time.Duration

object SummaryTables {

  private val buttonWidth: String = "w-60"

  val summaries: HtmlElement = div(
    counterInfoView.render,
    gaugeInfoView.render,
    histogramInfoView.render,
    summaryInfoView.render,
    setInfoView.render
  )

  private def diagramLink(k: MetricKey, f: DiagramConfig => Unit): HtmlElement = {
    val newCfg  = DiagramConfig(UUID.randomUUID().toString, k.longName, k, Duration.ofSeconds(5))
    val handler = Observer[dom.MouseEvent](onNext = _ => f(newCfg))
    a(
      href("#"),
      s"Add diagram",
      cls := "bg-blue-500 hover:bg-blue-700 text-white font-bold py-1 px-4 rounded text-center place-self-center",
      onClick --> handler
    )
  }

  private lazy val counterInfoView =
    WebTable.create[MetricKey, CounterInfo](
      cols = Chunk(
        WebTable.ColumnConfig[CounterInfo](
          width = buttonWidth,
          renderer = ci => diagramLink(ci.metric, AppState.addDiagram)
        ),
        WebTable.ColumnConfig[CounterInfo](
          "Name",
          renderer = ci => span(ci.metric.name)
        ),
        WebTable.ColumnConfig[CounterInfo](
          "Labels",
          renderer = ci => span(ci.metric.labelsAsString)
        ),
        WebTable.ColumnConfig[CounterInfo](
          "Current",
          renderer = ci => span(f"${ci.current.longValue()}%,d")
        )
      ),
      rk = _.metric
    )(AppState.counterInfo)

  private lazy val gaugeInfoView =
    WebTable.create[MetricKey, GaugeInfo](
      cols = Chunk(
        WebTable.ColumnConfig[GaugeInfo](
          width = buttonWidth,
          renderer = gi => diagramLink(gi.metric, AppState.addDiagram)
        ),
        WebTable.ColumnConfig[GaugeInfo](
          "Name",
          renderer = gi => span(gi.metric.name)
        ),
        WebTable.ColumnConfig[GaugeInfo](
          "Labels",
          renderer = gi => span(gi.metric.labelsAsString)
        ),
        WebTable.ColumnConfig[GaugeInfo](
          "Current",
          renderer = gi => span(f"${gi.current}%,8.3f")
        )
      ),
      rk = _.metric
    )(AppState.gaugeInfo)

  private lazy val histogramInfoView =
    WebTable.create[MetricKey, HistogramInfo](
      cols = Chunk(
        WebTable.ColumnConfig[HistogramInfo](
          width = buttonWidth,
          renderer = hi => diagramLink(hi.metric, AppState.addDiagram)
        ),
        WebTable.ColumnConfig[HistogramInfo](
          "Name",
          renderer = hi => span(hi.metric.name)
        ),
        WebTable.ColumnConfig[HistogramInfo](
          "Labels",
          renderer = hi => span(hi.metric.labelsAsString)
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
      rk = _.metric
    )(AppState.histogramInfo)

  private lazy val summaryInfoView = WebTable.create[MetricKey, SummaryInfo](
    cols = Chunk(
      WebTable.ColumnConfig[SummaryInfo](
        width = buttonWidth,
        renderer = si => diagramLink(si.metric, AppState.addDiagram)
      ),
      WebTable.ColumnConfig[SummaryInfo](
        "Name",
        renderer = si => span(si.metric.name)
      ),
      WebTable.ColumnConfig[SummaryInfo](
        "Labels",
        renderer = si => span(si.metric.labelsAsString)
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
    rk = _.metric
  )(AppState.summaryInfo)

  private lazy val setInfoView = WebTable.create[MetricKey, SetInfo](
    cols = Chunk(
      WebTable.ColumnConfig[SetInfo](
        width = buttonWidth,
        renderer = si => diagramLink(si.metric, AppState.addDiagram)
      ),
      WebTable.ColumnConfig[SetInfo](
        "Name",
        renderer = si => span(si.metric.name)
      ),
      WebTable.ColumnConfig[SetInfo](
        "Labels",
        renderer = si => span(si.metric.labelsAsString)
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
    rk = _.metric
  )(AppState.setInfo)
}
