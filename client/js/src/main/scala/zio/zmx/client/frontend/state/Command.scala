package zio.zmx.client.frontend.state

import zio.Chunk
import com.raquo.airstream.core.Observer
import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.model.MetricSummary
import zio.zmx.client.frontend.components._
import zio.zmx.client.frontend.model.PanelConfig
import zio.zmx.client.frontend.model.Layout._
import java.util.concurrent.atomic.AtomicInteger

sealed trait Direction
object Direction {
  case object Up   extends Direction {
    val up: String = "up"
  }
  case object Down extends Direction {
    val down: String = "down"
  }
}

sealed trait Command

object Command {

  case object Disconnect                             extends Command
  final case class Connect(url: String)              extends Command
  final case class RecordData(msg: MetricsMessage)   extends Command
  final case class SetTheme(t: Theme.DaisyTheme)     extends Command
  final case class ClosePanel(cfg: PanelConfig)      extends Command
  final case class SplitHorizontal(cfg: PanelConfig) extends Command
  final case class SplitVertical(cfg: PanelConfig)   extends Command
  final case class UpdateDashboard(cfg: PanelConfig) extends Command

  private val panelCount: AtomicInteger = new AtomicInteger(0)

  val observer = Observer[Command] {
    case Disconnect =>
      AppState.wsConnection.update {
        case None     => None
        case Some(ws) =>
          println("Disconnecting from server")
          ws.disconnectNow()
          None
      }
      AppState.resetState()

    case Connect(url) =>
      println(s"Connecting url to : [$url]")
      AppState.shouldConnect.set(true)
      AppState.connectUrl.set(url)
      AppState.wsConnection.update { _ =>
        val ws = WebsocketHandler.create(url)
        ws.reconnectNow()
        Some(ws)
      }

    case SetTheme(t) => AppState.theme.update(_ => t)

    // Tap into the incoming stream of MetricMessages and update the summary information
    // for the category the metric message belongs to
    case RecordData(msg) =>
      AppState.messages.emit(msg)
      MetricSummary.fromMessage(msg) match {
        case None    => // do nothing
        case Some(s) =>
          s match {
            case info: MetricSummary.CounterInfo   => AppState.counterInfos.update(_.updated(info.metric, info))
            case info: MetricSummary.GaugeInfo     => AppState.gaugeInfos.update(_.updated(info.metric, info))
            case info: MetricSummary.HistogramInfo => AppState.histogramInfos.update(_.updated(info.metric, info))
            case info: MetricSummary.SummaryInfo   => AppState.summaryInfos.update(_.updated(info.metric, info))
            case info: MetricSummary.SetInfo       => AppState.setCountInfos.update(_.updated(info.metric, info))
          }
      }

    case ClosePanel(cfg) =>
      AppState.dashBoard.update(db =>
        db.transform {
          case Dashboard.Cell(p) if p.id == cfg.id => Dashboard.Empty
        }
      )

    case SplitHorizontal(cfg) =>
      AppState.dashBoard.update(db =>
        db.transform {
          case c @ Dashboard.Cell(p) if p.id == cfg.id =>
            Dashboard.VGroup(
              Chunk(c, Dashboard.Cell(PanelConfig.EmptyConfig.create(s"New Panel - ${panelCount.incrementAndGet()}")))
            )
        }
      )

    case SplitVertical(cfg) =>
      AppState.dashBoard.update(db =>
        db.transform {
          case c @ Dashboard.Cell(p) if p.id == cfg.id =>
            Dashboard.HGroup(
              Chunk(c, Dashboard.Cell(PanelConfig.EmptyConfig.create(s"New Panel - ${panelCount.incrementAndGet()}")))
            )
        }
      )

    case UpdateDashboard(cfg) =>
      AppState.dashBoard.update(db =>
        db.transform {
          case Dashboard.Cell(p) if p.id == cfg.id =>
            Dashboard.Cell(cfg)
        }
      )
  }
}
