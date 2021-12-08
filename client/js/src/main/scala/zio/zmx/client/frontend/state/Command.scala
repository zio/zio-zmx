package zio.zmx.client.frontend.state

import zio.Chunk
import com.raquo.airstream.core.Observer
import zio.zmx.client.MetricsUpdate
import zio.zmx.client.frontend.components._
import zio.zmx.client.frontend.model.PanelConfig
import zio.zmx.client.frontend.model.Layout._
import java.util.concurrent.atomic.AtomicInteger
import com.raquo.airstream.eventbus.EventBus
import zio.zmx.client.frontend.model.TimeSeriesKey
import zio.zmx.client.frontend.model.TimeSeriesConfig
import zio.zmx.client.frontend.model.TimeSeriesEntry
import zio.zmx.client.frontend.model.LineChartModel
import zio.metrics.MetricKey
import zio.zmx.client.ClientMessage
import zio.zmx.client.ClientMessage._

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

  case object Disconnect                                                                            extends Command
  final case class Connect(url: String)                                                             extends Command
  final case class ServerMessage(msg: ClientMessage)                                                extends Command
  final case class SetTheme(t: Theme.DaisyTheme)                                                    extends Command
  final case class ClosePanel(cfg: PanelConfig)                                                     extends Command
  final case class SplitHorizontal(cfg: PanelConfig)                                                extends Command
  final case class SplitVertical(cfg: PanelConfig)                                                  extends Command
  final case class UpdateDashboard(cfg: PanelConfig)                                                extends Command
  final case class ConfigureTimeseries(panel: String, update: Map[TimeSeriesKey, TimeSeriesConfig]) extends Command
  final case class RecordPanelData(cfg: PanelConfig.DisplayConfig, entry: TimeSeriesEntry)          extends Command

  private val panelCount: AtomicInteger = new AtomicInteger(0)

  val observerN = Observer[Iterable[Command]](
    _.foreach(observer.onNext)
  )

  val observer = Observer[Command] {
    case Disconnect =>
      AppState.wsConnection.update {
        case None    => None
        case Some(_) =>
          println(AppState.clientID.now())
          AppState.clientID.now().foreach { id =>
            println("Sending Disconnect to Server")
            WebsocketHandler.sendCommand(ClientMessage.Disconnect(id))
          }
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
    case ServerMessage(msg) =>
      //println(s"Received Server message <$msg>")
      msg match {
        case MetricsNotification(update) =>
          update.foreach { update =>
            AppState.metricUpdates.update { updates =>
              updates.get(update.key) match {
                case None      =>
                  val bus = new EventBus[MetricsUpdate]
                  bus.emit(update)
                  updates.updated(update.key, bus)
                case Some(bus) =>
                  bus.emit(update)
                  updates
              }
            }
          }
        case Connected(id)               => AppState.clientID.set(Some(id))
        case AvailableMetrics(keys)      => AppState.availableMetrics.set(Chunk.fromIterable(keys))
        case o                           => println(s"Received unhandled message from Server : [$o]")
      }

    case ClosePanel(cfg) =>
      AppState.dashBoard.update(db =>
        db.transform {
          case Dashboard.Cell(p) if p.id == cfg.id => Dashboard.Empty
        } match {
          case Dashboard.Empty => AppState.defaultDashboard
          case d               => d
        }
      )
      AppState.timeSeries.update(_.removed(cfg.id))
      AppState.recordedData.update(_.removed(cfg.id))

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
      cfg match {
        case cfg: PanelConfig.DisplayConfig =>
          AppState.recordedData.update { cur =>
            val data = cur.get(cfg.id) match {
              case None    => LineChartModel(cfg.maxSamples)
              case Some(m) => m.updateMaxSamples(cfg.maxSamples)
            }
            cur.updated(cfg.id, data)
          }
        case _                              => // do nothing
      }

    case ConfigureTimeseries(panel, update) =>
      AppState.timeSeries.update(_.updated(panel, update))
      AppState.recordedData.now().get(panel).foreach { m =>
        println(s"Update : $update")
        val toRemove: Chunk[MetricKey] =
          Chunk.fromIterable(m.data.keySet.filter(k => !update.contains(k)).map(_.metric)).distinct

        val updatedModel =
          toRemove.foldLeft(m) { case (cur, k) =>
            println(s"Removing [$k]")
            cur.removeMetric(k)
          }

        AppState.recordedData.update(_.updated(panel, updatedModel))
      }

    case RecordPanelData(cfg, entry) =>
      AppState.recordedData.update(cur =>
        cur.updated(
          cfg.id,
          cur.getOrElse(cfg.id, LineChartModel(cfg.maxSamples)).recordEntry(entry)
        )
      )

  }
}
