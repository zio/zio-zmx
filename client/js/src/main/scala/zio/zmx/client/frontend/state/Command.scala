package zio.zmx.client.frontend.state

import java.util.concurrent.atomic.AtomicInteger
import scala.annotation.tailrec
import com.raquo.airstream.core.Observer

import zio.Chunk
import zio.metrics.MetricKey
import zio.zmx.client.frontend.components._
import zio.zmx.client.frontend.model.PanelConfig
import zio.zmx.client.frontend.model.Layout._
import zio.zmx.client.frontend.model.Layout.Dashboard._
import zio.zmx.client.frontend.model.TimeSeriesKey
import zio.zmx.client.frontend.model.TimeSeriesConfig
import zio.zmx.client.frontend.model.TimeSeriesEntry
import zio.zmx.client.frontend.model.LineChartModel
import zio.zmx.client.ClientMessage
import zio.zmx.client.ClientMessage._
import zio.zmx.client.frontend.utils.DomUtils.Color

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
  final case class ImportDashboard(cfg: Dashboard[PanelConfig])                                     extends Command
  final case class UpdateDashboard(cfg: PanelConfig)                                                extends Command
  final case class ConfigureTimeseries(panel: String, update: Map[TimeSeriesKey, TimeSeriesConfig]) extends Command
  final case class RecordPanelData(subId: String, entry: Chunk[TimeSeriesEntry])                    extends Command

  private val panelCount: AtomicInteger = new AtomicInteger(0)

  private def appId: Option[String]                         = AppState.clientID.now()
  private def sendCommand(f: String => ClientMessage): Unit =
    appId.map(f).foreach(WebsocketHandler.sendCommand)

  val observer: Observer[Command] = Observer[Command] {
    case Disconnect =>
      AppState.wsConnection.update {
        case None    =>
          None
        case Some(_) =>
          sendCommand(ClientMessage.Disconnect)
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
      msg match {
        case update: MetricsNotification =>
          val entries = TimeSeriesEntry.fromMetricsNotification(update)
          observer.onNext(RecordPanelData(update.subId, entries))
        case Connected(cltId)            => AppState.clientID.set(Some(cltId))
        case AvailableMetrics(keys)      => AppState.availableMetrics.set(Chunk.fromIterable(keys))
        case o                           => println(s"Received unhandled message from Server : [$o]")
      }

    case ClosePanel(cfg) =>
      AppState.dashBoard.update(db =>
        db.transform {
          case Dashboard.Cell(p) if p.id == cfg.id =>
            sendCommand(ClientMessage.RemoveSubscription(_, cfg.id))
            Dashboard.Empty
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

    case ImportDashboard(cfg) =>
      AppState.dashBoard.set(cfg)

      @tailrec
      def loop(
        config: Dashboard[PanelConfig],
        remainingConfigs: List[Dashboard[PanelConfig]]
      ): Unit =
        config match {
          case Cell(cfg: PanelConfig.DisplayConfig) =>
            sendCommand(
              ClientMessage.Subscription(_, cfg.id, cfg.metrics, cfg.refresh)
            )
            AppState.recordedData.update { cur =>
              val data =
                cur.get(cfg.id) match {
                  case None    =>
                    LineChartModel(cfg.maxSamples)
                  case Some(m) =>
                    m.updateMaxSamples(cfg.maxSamples)
                }
              cur.updated(cfg.id, data)
            }

            remainingConfigs match {
              case next :: remainingConfigs =>
                loop(next, remainingConfigs)
              case Nil                      => // we're done
            }

          case HGroup(dashboards) =>
            dashboards.toList match {
              case next :: moreConfigs =>
                loop(next, moreConfigs ++ remainingConfigs)
              case Nil                 =>
                remainingConfigs match {
                  case next :: remainingConfigs =>
                    loop(next, remainingConfigs)
                  case Nil                      => // we're done
                }
            }

          case VGroup(dashboards) =>
            dashboards.toList match {
              case next :: moreConfigs =>
                loop(next, moreConfigs ++ remainingConfigs)
              case Nil                 =>
                remainingConfigs match {
                  case next :: remainingConfigs =>
                    loop(next, remainingConfigs)
                  case Nil                      => // we're done
                }
            }

          case _ =>
            remainingConfigs match {
              case next :: remainingConfigs =>
                loop(next, remainingConfigs)
              case Nil                      => // we're done
            }
        }
      loop(cfg, Nil)

    case UpdateDashboard(cfg) =>
      AppState.dashBoard.update(db =>
        db.transform {
          case Dashboard.Cell(p) if p.id == cfg.id =>
            Dashboard.Cell(cfg)
        }
      )
      cfg match {
        case cfg: PanelConfig.DisplayConfig =>
          sendCommand(ClientMessage.Subscription(_, cfg.id, cfg.metrics, cfg.refresh))
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

    case RecordPanelData(id, entries) =>
      AppState.dashBoard.now().find(_.id == id) match {
        case Some(cfg: PanelConfig.DisplayConfig) =>
          AppState.recordedData.update { cur =>
            val model   = cur.getOrElse(id, LineChartModel(cfg.maxSamples))
            println(s"Recording data for panel <$id> : <${entries.size}> entries, $model")
            val updated = entries.foldLeft(model) { case (m, e) => m.recordEntry(e) }
            cur.updated(id, updated)
          }
          AppState.timeSeries.update { cur =>
            val tsCfgs  = cur.getOrElse(id, Map.empty)
            val updated = entries.foldLeft(tsCfgs) { case (cfgs, e) =>
              if (cfgs.contains(e.key)) cfgs
              else cfgs.updated(e.key, TimeSeriesConfig(e.key, Color.random, 0.3))
            }
            cur.updated(id, updated)
          }
          AppState.updatedData.emit(id)

        case _ => // do nothing
      }
  }

  val observerN: Observer[Iterable[Command]] = Observer(
    _.foreach(observer.onNext)
  )
}
