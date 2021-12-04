package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.model.PanelConfig._
import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.state.{ AppState, Command }
import zio.zmx.client.MetricsMessage
import zio.metrics.MetricKey
import scala.util.Random
import zio.zmx.client.frontend.utils.DomUtils

class DataTracker(cfg: DisplayConfig, update: DisplayConfig => Unit) {

  println(s"Created DataTracker for ${cfg.id}")

  private val rnd = new Random()
  def nextColor   = DomUtils.Color(
    rnd.nextInt(210) + 30,
    rnd.nextInt(210) + 30,
    rnd.nextInt(210) + 30
  )

  // Add a new Timeseries, only if the graph does not contain a line for the key yet
  // The key is the string representation of a metrickey, in the case of histograms, summaries and setcounts
  // it identifies a single stream of samples within the collection of the metric
  def recordData(cfg: DisplayConfig, entry: TimeSeriesEntry): Unit =
    Command.observer.onNext(Command.RecordPanelData(cfg, entry))

  val metricStream: (DisplayConfig, MetricKey) => EventSource[MetricsMessage] = (cfg, m) =>
    AppState.metricMessages.now().get(m) match {
      case None    => EventStream.empty
      case Some(s) => s.events.throttle(cfg.refresh.toMillis().intValue())
    }

  def updateFromMetricsStream =
    cfg.metrics.map(m =>
      metricStream(cfg, m) --> Observer[MetricsMessage] { msg =>
        val tsConfigs: Map[TimeSeriesKey, TimeSeriesConfig] =
          AppState.timeSeries.now().getOrElse(cfg.id, Map.empty)

        val entries = TimeSeriesEntry.fromMetricsMessage(msg)
        val noCfg   = entries.filter(e => !tsConfigs.contains(e.key))

        if (noCfg.isEmpty) {
          entries.foreach(e => recordData(cfg, e))
          update(cfg)
        } else {
          val newCfgs = noCfg.map(e => (e.key, TimeSeriesConfig(e.key, nextColor, 0.3))).toMap
          Command.observer.onNext(Command.ConfigureTimeseries(cfg.id, tsConfigs ++ newCfgs))
        }
      }
    )
}
