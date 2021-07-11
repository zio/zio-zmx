package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._
import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.AppState
import zio.Chunk
import zio.duration._
import zio.zmx.client.frontend.AppDataModel
import zio.zmx.client.MetricsMessage.CounterChange
import zio.zmx.client.frontend.utils.Implicits._
import zio.zmx.client.MetricsMessage.GaugeChange
import zio.zmx.client.MetricsMessage.HistogramChange
import zio.zmx.client.MetricsMessage.SummaryChange
import zio.zmx.client.MetricsMessage.SetChange
sealed trait DiagramView {
  def render(): HtmlElement
}

object DiagramView {

  def counterDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.CounterChange](key, AppState.counterMessages, 5.seconds)

  def gaugeDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.GaugeChange](key, AppState.gaugeMessages, 5.seconds)

  def histogramDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.HistogramChange](key, AppState.histogramMessages, 5.seconds)

  def summaryDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.SummaryChange](key, AppState.summaryMessages, 5.seconds)

  def setDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.SetChange](key, AppState.setMessages, 5.seconds)

  private class DiagramViewImpl[M <: MetricsMessage](key: String, events: EventStream[M], interval: Duration)
      extends DiagramView {

    private def getKey(m: MetricsMessage): String = m match {
      case GaugeChange(key, _, _, _)   => key.name + AppDataModel.MetricSummary.labels(key.tags)
      case CounterChange(key, _, _, _) => key.name + AppDataModel.MetricSummary.labels(key.tags)
      case HistogramChange(key, _, _)  => key.name + AppDataModel.MetricSummary.labels(key.tags)
      case SummaryChange(key, _, _)    => key.name + AppDataModel.MetricSummary.labels(key.tags)
      case SetChange(key, _, _)        => key.name + AppDataModel.MetricSummary.labels(key.tags)
    }

    private lazy val current: Var[Option[(Long, M)]] = {
      val res: Var[Option[(Long, M)]] = Var(None)

      val tracker = events.map { msg =>
        if (getKey(msg) == key) {
          val pair: (Long, M) = (msg.when.toEpochMilli(), msg)
          Some(pair)
        } else None
      }

      (tracker.toSignal(None).foreach {
        _ match {
          case None    => ()
          case Some(p) =>
            res.update(_ => Some(p))
        }
      })(unsafeWindowOwner)

      res
    }

    private lazy val sampled: Var[Chunk[(Long, M)]] = {
      val res: Var[Chunk[(Long, M)]] = Var(Chunk.empty)

      (current.signal.changes
        .throttle(interval.toMillis().intValue())
        .toSignal(None)
        .foreach(_ match {
          case None        => ()
          case Some(entry) => res.update(chunk => (chunk :+ entry).takeRight(10))
        }))(unsafeWindowOwner)

      res
    }

    override def render(): HtmlElement = {

      def renderElement(t: Long, data: (Long, M), s: Signal[(Long, M)]): HtmlElement = div(
        span(t.toString()),
        span(" -- "),
        span(child <-- s.map(_._2.toString()))
      )

      div(
        cls := "bg-gray-900 text-gray-50 rounded my-3 p-3",
        span(
          cls := "text-2xl font-bold my-2",
          s"A diagram for $key"
        ),
        children <-- sampled.signal.split(_._1)(renderElement)
      )
    }
  }
}
