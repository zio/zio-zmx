package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._
import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.AppState
import zio.Chunk
import zio.duration._
import zio.zmx.client.frontend.AppDataModel
import zio.zmx.client.MetricsMessage.CounterChange
import scala.scalajs.js.Date
import zio.zmx.client.frontend.utils.Implicits._
sealed trait DiagramView {
  def render(): HtmlElement
}

object DiagramView {

  def counterDiagram(key: String): DiagramView =
    new DiagramViewImpl[MetricsMessage.CounterChange](key, AppState.counterMessages.signal, 5.seconds)

  private class DiagramViewImpl[M <: MetricsMessage](key: String, events: Signal[Option[M]], interval: Duration)
      extends DiagramView {

    private lazy val sampled: Var[Chunk[(Long, M)]] = {
      val res: Var[Chunk[(Long, M)]] = Var(Chunk.empty)

      (events.changes
        .throttle(interval.toMillis().intValue())
        .toSignal(None)
        .changes
        .collect { case Some(msg) => msg }
        .foreach { msg =>
          msg match {
            case cMsg: CounterChange =>
              if (cMsg.key.name + AppDataModel.MetricSummary.labels(cMsg.key.tags) == key) {
                val pair: (Long, M) = (new Date().getTime().longValue(), msg)
                println(pair.toString())
                res.update(chunk => (chunk :+ pair).takeRight(10))
              }
            case _                   => ()
          }
        })(unsafeWindowOwner)

      res
    }

    override def render(): HtmlElement = {

      def renderElement(t: Long, data: (Long, M), s: Signal[(Long, M)]): HtmlElement = div(
        span(t.toString()),
        span(" -- "),
        span(child <-- s.map(_._2.toString()))
      )

      div(
        s"A diagram for $key",
        children <-- sampled.signal.split(_._1)(renderElement)
      )
    }
  }
}
