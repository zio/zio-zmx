package zio.zmx.client.frontend.state

import zio._

import com.raquo.laminar.api.L._

import zio.zmx.client.MetricsMessage
import zio.zmx.client.frontend.model._
import zio.zmx.client.frontend.model.MetricSummary._

import zio.metrics.MetricKey

object Theme {
  sealed trait DaisyTheme {
    def name: String
  }

  object DaisyTheme {
    final case object Dark      extends DaisyTheme { override def name: String = "dark"      }
    final case object Halloween extends DaisyTheme { override def name: String = "halloween" }
    final case object Light     extends DaisyTheme { override def name: String = "light"     }
    final case object Emerald   extends DaisyTheme { override def name: String = "emerald"   }
    final case object CupCake   extends DaisyTheme { override def name: String = "cupcake"   }
    final case object Dracula   extends DaisyTheme { override def name: String = "dracula"   }
  }

  val allThemes: Chunk[DaisyTheme] = {
    import DaisyTheme._
    Chunk(Dark, Halloween, Light, Emerald, CupCake, Dracula)
  }
}

object AppState {

  // The theme that is currently used
  val theme: Var[Theme.DaisyTheme] = Var(Theme.DaisyTheme.Halloween)

  // This reflects whether the app is currently connected, it is set by the
  // WS handler when it has established a connection
  val connected: Var[Boolean] = Var(false)

  // This reflects if the user has hit the connect button and we shall try to connect
  // to the configured url
  val shouldConnect: Var[Boolean] = Var(true)

  // When we do have a WS connection this is our source of events
  // TODO: We would like to make the underlying protocol more efficient
  val messages: EventBus[MetricsMessage] = new EventBus[MetricsMessage]

  // The WS URL we want to consume events from
  val connectUrl: Var[String] = Var("ws://localhost:8080/ws")

  // The currently displayed diagrams (order is important)
  val diagrams: Var[Chunk[DiagramConfig]] = Var(Chunk.empty)

  val counterInfos: Var[Map[MetricKey, CounterInfo]]     = Var(Map.empty)
  val gaugeInfos: Var[Map[MetricKey, GaugeInfo]]         = Var(Map.empty)
  val histogramInfos: Var[Map[MetricKey, HistogramInfo]] = Var(Map.empty)
  val summaryInfos: Var[Map[MetricKey, SummaryInfo]]     = Var(Map.empty)
  val setCountInfos: Var[Map[MetricKey, SetInfo]]        = Var(Map.empty)

  // Reset everything - is usually called upon disconnect
  def resetState(): Unit = {
    shouldConnect.set(false)
    diagrams.set(Chunk.empty)
    counterInfos.set(Map.empty)
    gaugeInfos.set(Map.empty)
    histogramInfos.set(Map.empty)
    summaryInfos.set(Map.empty)
    setCountInfos.set(Map.empty)
  }
}
