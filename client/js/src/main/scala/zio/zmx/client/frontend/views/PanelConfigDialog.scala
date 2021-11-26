package zio.zmx.client.frontend.views

import zio.Chunk
import zio.metrics.MetricKey

import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.state.Command
import zio.zmx.client.frontend.model.PanelConfig.DisplayConfig
import zio.zmx.client.frontend.state.AppState

object PanelConfigDialog {

  def render($cfg: Signal[DisplayConfig], id: String): HtmlElement =
    new PanelConfigDialogImpl($cfg, id).render()

  private class PanelConfigDialogImpl($cfg: Signal[DisplayConfig], dlgId: String) {

    // The current title for the panel
    private val curTitle: Var[String]                  = Var("")
    // The currently selected metrics
    private val selectedMetrics: Var[Chunk[MetricKey]] = Var(Chunk.empty)

    // Whether to show the counters available for selection
    private val showCounters: Var[Boolean]   = Var(true)
    // Whether to show the counters available for selection
    private val showGauges: Var[Boolean]     = Var(true)
    // Whether to show the gauges available for selection
    private val showHistograms: Var[Boolean] = Var(true)
    // Whether to show the summaries available for selection
    private val showSummaries: Var[Boolean]  = Var(true)
    // Whether to show the set counts available for selection
    private val showSetCounts: Var[Boolean]  = Var(true)

    // Convenience method to create a filtered signal of a chunk of selectable metrics
    private def availableMetrics(
      checked: Signal[Boolean],
      metrics: Signal[Chunk[MetricKey]]
    ): Signal[Chunk[MetricKey]] = {

      // First filter by the toggle
      val shown = metrics.combineWithFn[Boolean, Chunk[MetricKey]](checked) { case (m, s) =>
        if (s) m else Chunk.empty
      }

      // Then filter out all metrics that are already displayed in the diagram
      shown.combineWithFn[Chunk[MetricKey], Chunk[MetricKey]](selectedMetrics.signal) { case (known, selected) =>
        known.filter(k => !selected.contains(k))
      }
    }

    private val availableCounters   = availableMetrics(showCounters.signal, AppState.knownCounters)
    private val availableGauges     = availableMetrics(showGauges.signal, AppState.knownGauges)
    private val availableHistograms = availableMetrics(showHistograms.signal, AppState.knownHistograms)
    private val availableSummaries  = availableMetrics(showSummaries.signal, AppState.knownSummaries)
    private val availableSetCounts  = availableMetrics(showSetCounts.signal, AppState.knownSetCounts)

    private val metricSelected: Observer[MetricKey] = Observer[MetricKey] { key =>
      selectedMetrics.update(cur => if (!cur.contains(key)) cur :+ key else cur)
    }

    private val metricRemoved: Observer[MetricKey] = Observer[MetricKey] { key =>
      selectedMetrics.update(_.filter(!_.equals(key)))
    }

    private def selectSections: HtmlElement = {
      def selector(title: String, doCheck: Var[Boolean]): HtmlElement =
        div(
          cls := "form-control",
          label(cls := "label", span(cls := "label-text text-xl", title)),
          input(
            cls := "toggle toggle-lg toggle-secondary",
            tpe := "checkbox",
            checked <-- doCheck.signal,
            inContext { el =>
              onClick.mapTo(el.ref.checked) --> doCheck
            }
          )
        )

      div(
        cls := "w-full card bg-secondary text-secondary-content bordered p-4 mt-2 grid grid-cols-5",
        selector("Show Counters", showCounters),
        selector("Show Gauges", showGauges),
        selector("Show Histograms", showHistograms),
        selector("Show Summaries", showSummaries),
        selector("Show Set Counts", showSetCounts)
      )
    }

    def render(): HtmlElement =
      div(
        idAttr := dlgId,
        cls := "modal",
        child <-- $cfg.map { cfg =>
          div(
            cls := "modal-box max-w-full m-12 border-2 flex flex-col bg-accent-focus text-accent-content",
            div(
              cls := "border-b-2",
              span("Panel configuration")
            ),
            div(
              cls := "flex-grow",
              div(
                cls := "form-control",
                label(cls := "label", span(cls := "label-text text-xl", "Title")),
                input(
                  tpe := "text",
                  cls := "input input-primary input-bordered",
                  placeholder("Enter Diagram title"),
                  value := cfg.title,
                  onMountCallback { _ =>
                    curTitle.set(cfg.title)
                    selectedMetrics.set(cfg.metrics)
                  },
                  onInput.mapToValue --> curTitle
                )
              ),
              MetricsSelector("Configured metrics", metricRemoved).render(selectedMetrics.signal),
              div(
                cls := "card bordered border-2 border-secondary form-control p-3 mt-2",
                label(cls := "label", span(cls := "label-text text-xl", "Select Metrics to Display")),
                selectSections,
                MetricsSelector("Available Counters", metricSelected, "secondary").render(availableCounters),
                MetricsSelector("Available Gauges", metricSelected, "secondary").render(availableGauges),
                MetricsSelector("Available Histograms", metricSelected, "secondary").render(availableHistograms),
                MetricsSelector("Available Summaries", metricSelected, "secondary").render(availableSummaries),
                MetricsSelector("Available Set Counts", metricSelected, "secondary").render(availableSetCounts)
              )
            ),
            div(
              cls := "modal-action",
              a(
                href := "#",
                cls := "btn btn-secondary",
                onClick.map(_ => cfg.title) --> curTitle,
                "Cancel"
              ),
              a(
                href := "#",
                cls := "btn btn-primary",
                onClick.map { _ =>
                  val curTimeseries = AppState.timeSeries.now().getOrElse(cfg.id, Map.empty)
                  val curMetrics    = selectedMetrics.now()

                  val newCfg = cfg.copy(
                    title = curTitle.now(),
                    metrics = curMetrics
                  )
                  Seq(
                    Command.UpdateDashboard(newCfg),
                    Command.ConfigureTimeseries(
                      cfg.id,
                      curTimeseries.filter { case (k, _) => curMetrics.contains(k.metric) }
                    )
                  )
                } --> Command.observerN,
                "Apply"
              )
            )
          )
        }
      )
  }
}
