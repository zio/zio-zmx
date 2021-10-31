package zio.zmx.client.frontend.state

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.model.MetricSummary
import zio.zmx.client.frontend.model.MetricSummary._

import zio.zmx.client.MetricsMessage
import zio.zmx.client.MetricsMessage._

object MessageHub {
  // The overall stream of incoming metric updates
  lazy val messages: EventBus[MetricsMessage] = new EventBus[MetricsMessage]

  // The stream of metric updates specifically for each known metric type
  lazy val counterMessages: EventStream[CounterChange]     =
    messages.events.collect { case chg: CounterChange => chg }
  lazy val gaugeMessages: EventStream[GaugeChange]         =
    messages.events.collect { case chg: GaugeChange => chg }
  lazy val histogramMessages: EventStream[HistogramChange] =
    messages.events.collect { case chg: HistogramChange => chg }
  lazy val summaryMessages: EventStream[SummaryChange]     =
    messages.events.collect { case chg: SummaryChange => chg }
  lazy val setMessages: EventStream[SetChange]             =
    messages.events.collect { case chg: SetChange => chg }

  // Streams of events specific for the metric types mapped to info objects, so that we can feed the
  // stream of info objects into our table views
  lazy val counterInfo: EventStream[CounterInfo]           =
    counterMessages.map(chg => MetricSummary.fromMessage(chg)).collect { case Some(s: MetricSummary.CounterInfo) =>
      s
    }

  lazy val gaugeInfo: EventStream[GaugeInfo] =
    gaugeMessages.map(chg => MetricSummary.fromMessage(chg)).collect { case Some(s: MetricSummary.GaugeInfo) =>
      s
    }

  lazy val histogramInfo: EventStream[HistogramInfo] =
    histogramMessages.map(chg => MetricSummary.fromMessage(chg)).collect { case Some(s: MetricSummary.HistogramInfo) =>
      s
    }

  lazy val summaryInfo: EventStream[SummaryInfo] =
    summaryMessages.map(chg => MetricSummary.fromMessage(chg)).collect { case Some(s: MetricSummary.SummaryInfo) =>
      s
    }

  lazy val setInfo: EventStream[SetInfo] =
    setMessages.map(chg => MetricSummary.fromMessage(chg)).collect { case Some(s: MetricSummary.SetInfo) =>
      s
    }

}
