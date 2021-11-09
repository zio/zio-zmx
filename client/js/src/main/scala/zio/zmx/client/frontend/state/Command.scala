package zio.zmx.client.frontend.state

import com.raquo.airstream.core.Observer
import zio.zmx.client.frontend.model.DiagramConfig
import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.model.MetricSummary

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

  case object Disconnect                                                 extends Command
  final case class Connect(url: String)                                  extends Command
  final case class AddDiagram(cfg: DiagramConfig)                        extends Command
  final case class UpdateDiagram(cfg: DiagramConfig)                     extends Command
  final case class RemoveDiagram(cfg: DiagramConfig)                     extends Command
  final case class RecordData(msg: MetricsMessage)                       extends Command
  final case class MoveDiagram(cfg: DiagramConfig, direction: Direction) extends Command

  val observer = Observer[Command] {
    case Disconnect =>
      println("Disconnecting from server")
      AppState.resetState()

    case Connect(url)              =>
      println(s"Connecting url to : [$url]")
      AppState.shouldConnect.set(true)
      AppState.dashboardConfig.update(_.copy(connectUrl = url))

    // Make sure the diagram is appended to the list of diagrams and has the correct index
    case AddDiagram(d)             =>
      AppState.dashboardConfig.update(cfg =>
        cfg.copy(diagrams = cfg.diagrams :+ d.copy(displayIndex = cfg.diagrams.size))
      )

    // Make sure that the diagrams stay in the same order as they have been before
    case UpdateDiagram(d)          =>
      AppState.dashboardConfig.update(cfg =>
        cfg.copy(diagrams = (cfg.diagrams.filter(!_.id.equals(d.id)) :+ d).sortBy(_.displayIndex))
      )

    // Remove the diagram and re-index the remaining diagrams
    case RemoveDiagram(d)          =>
      AppState.dashboardConfig.update(cfg =>
        cfg.copy(diagrams = (cfg.diagrams.filter(!_.id.equals(d.id)).zipWithIndex.map(_._1)))
      )

    // Update diagram displayIndex
    case MoveDiagram(d, direction) =>
      AppState.dashboardConfig.update(cfg =>
        direction match {
          case Direction.Up   => cfg
          case Direction.Down => cfg
        }
      )

    // Tap into the incoming stream of MetricMessages and update the summary information
    // for the category the metric message belongs to
    case RecordData(msg)           =>
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

  }
}
