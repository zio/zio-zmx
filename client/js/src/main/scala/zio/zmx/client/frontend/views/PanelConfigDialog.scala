package zio.zmx.client.frontend.views

import java.time.Duration

import com.raquo.laminar.api.L._

import zio.Chunk
import zio.metrics.MetricKey
import zio.zmx.client.frontend.model.PanelConfig.DisplayConfig
import zio.zmx.client.frontend.state.AppState
import zio.zmx.client.frontend.state.Command
import zio.zmx.client.frontend.utils.Implicits._

object PanelConfigDialog {

  def render($cfg: Signal[DisplayConfig], id: String): HtmlElement =
    new PanelConfigDialogImpl($cfg, id).render()

  private class PanelConfigDialogImpl($cfg: Signal[DisplayConfig], dlgId: String) {

    // The current title for the panel
    private val curTitle: Var[String]                        = Var("")
    // The currently selected metrics
    private val selectedMetrics: Var[Set[MetricKey.Untyped]] = Var(Set.empty)
    private val curRefresh: Var[String]                      = Var("")
    private val curSamples: Var[String]                      = Var("")
    private val curFilter: Var[String]                       = Var("")

    // Convenience method to create a filtered signal of a chunk of selectable metrics
    private def availableMetrics(
      metrics: Signal[Set[MetricKey.Untyped]],
    ): Signal[Set[MetricKey.Untyped]] =
      // Then filter out all metrics that are already displayed in the diagram
      metrics.combineWithFn[Set[MetricKey.Untyped], String, Set[MetricKey.Untyped]](
        selectedMetrics.signal,
        curFilter.signal,
      ) { case (known, selected, textFilter) =>
        known.filter { k =>
          val name    = k.longName
          val words   = textFilter.trim().split(" ")
          val matches = words.isEmpty || words.forall(name.contains)
          matches && !selected.contains(k)
        }
      }

    private val availableCounters   = availableMetrics(AppState.knownCounters)
    private val availableGauges     = availableMetrics(AppState.knownGauges)
    private val availableHistograms = availableMetrics(AppState.knownHistograms)
    private val availableSummaries  = availableMetrics(AppState.knownSummaries)
    private val availableSetCounts  = availableMetrics(AppState.knownSetCounts)

    private val allKnownMetrics =
      Signal.combine(
        AppState.knownCounters,
        AppState.knownGauges,
        AppState.knownHistograms,
        AppState.knownSummaries,
        AppState.knownSetCounts,
      )

    private val metricSelected: Observer[MetricKey.Untyped] = Observer[MetricKey.Untyped] { key =>
      selectedMetrics.update(cur => if (!cur.contains(key)) cur + key else cur)
    }

    private val metricRemoved: Observer[MetricKey.Untyped] = Observer[MetricKey.Untyped] { key =>
      selectedMetrics.update(_.filter(!_.equals(key)))
    }

    private def configValues(cfg: DisplayConfig): HtmlElement =
      div(
        cls := "flex flex-row",
        div(
          cls := "flex-grow",
          div(
            cls := "form-control",
            label(cls := "label", span(cls := "label-text text-xl", "Title")),
            input(
              tpe     := "text",
              cls     := "input input-primary input-bordered",
              placeholder("Enter Diagram title"),
              value   := cfg.title,
              onMountCallback { _ =>
                curTitle.set(cfg.title)
                selectedMetrics.set(cfg.metrics)
              },
              onInput.mapToValue --> curTitle,
            ),
          ),
        ),
        div(
          cls := "flex-none",
          div(
            cls := "form-control",
            label(cls := "label", span(cls := "label-text text-xl", "Refresh")),
            input(
              tpe     := "number",
              cls     := "input input-primary input-bordered",
              value   := s"${cfg.refresh.getSeconds}",
              onInput.mapToValue --> curRefresh,
              onMountCallback(_ => curRefresh.set(s"${cfg.refresh.getSeconds.intValue()}")),
            ),
          ),
        ),
        div(
          cls := "flex-none",
          div(
            cls := "form-control",
            label(cls := "label", span(cls := "label-text text-xl", "Samples")),
            input(
              tpe     := "number",
              cls     := "input input-primary input-bordered",
              value   := s"${cfg.maxSamples}",
              onInput.mapToValue --> curSamples,
              onMountCallback(_ => curSamples.set(s"${cfg.maxSamples}")),
            ),
          ),
        ),
      )

    def render(): HtmlElement =
      div(
        idAttr := dlgId,
        cls    := "modal",
        child <-- $cfg.map { cfg =>
          div(
            cls := "modal-box max-w-full h-5/6 mx-12 border-2 flex flex-col bg-accent-focus text-accent-content",
            div(
              cls := "border-b-2",
              span("Panel Configuration"),
            ),
            div(
              cls := "flex flex-col flex-grow overflow-y-auto",
              configValues(cfg),
              MetricsSelector(
                "Selected Metrics (click to remove)",
                metricRemoved,
                style = "primary",
              ).render(selectedMetrics.signal),
              div(
                cls := "form-control mt-2 flex flex-col",
                div(
                  cls := "form-control flex flex-row my-2",
                  label(cls     := "label flex-none", span(cls := "label-text text-xl", "Metrics Filter:")),
                  input(
                    tpe         := "text",
                    cls         := "flex-grow input input-primary input-bordered ml-3",
                    placeholder := "Type keywords here to filter...",
                    value <-- curFilter,
                    onInput.mapToValue --> curFilter,
                  ),
                ),
                label(
                  cls := "label flex-none",
                  span(
                    cls := "label-text text-xl",
                    // TODO: improve this
                    child <-- allKnownMetrics.map {
                      case (Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty) =>
                        s"Fetching metric list from server..."
                      case _                                                                 =>
                        "Click on a metric to add."
                    },
                  ),
                ),
              ),
              div(
                cls := "flex-grow",
                div(
                  cls := "max-w-full max-h-full",
                  MetricsSelector("Available Counters", metricSelected)
                    .render(availableCounters),
                  MetricsSelector("Available Gauges", metricSelected)
                    .render(availableGauges),
                  MetricsSelector("Available Histograms", metricSelected)
                    .render(availableHistograms),
                  MetricsSelector("Available Summaries", metricSelected)
                    .render(availableSummaries),
                  MetricsSelector("Available Set Counts", metricSelected)
                    .render(availableSetCounts),
                ),
              ),
            ),
            div(
              cls := "modal-action",
              a(
                href := "#",
                cls  := "btn btn-secondary",
                onClick.map(_ => cfg.title) --> curTitle,
                "Cancel",
              ),
              a(
                href := "#",
                cls  := "btn btn-primary",
                cls.toggle("btn-disabled") <-- selectedMetrics.signal.map(_.isEmpty),
                onClick.map { _ =>
                  val curTimeseries = AppState.timeSeries.now().getOrElse(cfg.id, Map.empty)
                  val newMetrics    = selectedMetrics.now()
                  val newRefresh    = Duration.ofSeconds(curRefresh.now().toLong)
                  val newSamples    = curSamples.now().toInt

                  val newCfg = cfg.copy(
                    title = curTitle.now(),
                    metrics = newMetrics,
                    refresh = newRefresh,
                    maxSamples = newSamples,
                  )
                  Seq(
                    Command.UpdateDashboard(newCfg),
                    Command.ConfigureTimeseries(
                      cfg.id,
                      curTimeseries.filter { case (k, _) => newMetrics.contains(k.metric) },
                    ),
                  )
                } --> Command.observerN,
                "Apply",
              ),
            ),
            div(
              cls := Seq("alert", "alert-error"),
              cls.toggle("visibility: hidden") <-- selectedMetrics.signal.map(_.nonEmpty),
              div(
                cls := "flex-1",
                label("No metrics selected."),
              ),
            ),
          )
        },
      )
  }
}
