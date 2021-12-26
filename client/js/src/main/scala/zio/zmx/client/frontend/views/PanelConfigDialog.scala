package zio.zmx.client.frontend.views

import java.time.Duration
import zio.Chunk
import zio.metrics.MetricKey

import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.state.Command
import zio.zmx.client.frontend.model.PanelConfig.DisplayConfig
import zio.zmx.client.frontend.state.AppState
import zio.zmx.client.frontend.utils.Implicits._

object PanelConfigDialog {

  def render($cfg: Signal[DisplayConfig], id: String): HtmlElement =
    new PanelConfigDialogImpl($cfg, id).render()

  private class PanelConfigDialogImpl($cfg: Signal[DisplayConfig], dlgId: String) {

    // The current title for the panel
    private val curTitle: Var[String]                  = Var("")
    // The currently selected metrics
    private val selectedMetrics: Var[Chunk[MetricKey]] = Var(Chunk.empty)
    private val curRefresh: Var[String]                = Var("")
    private val curSamples: Var[String]                = Var("")
    private val curFilter: Var[String]                 = Var("")

    // Convenience method to create a filtered signal of a chunk of selectable metrics
    private def availableMetrics(
      metrics: Signal[Chunk[MetricKey]]
    ): Signal[Chunk[MetricKey]] =
      // Then filter out all metrics that are already displayed in the diagram
      metrics.combineWithFn[Chunk[MetricKey], String, Chunk[MetricKey]](selectedMetrics.signal, curFilter.signal) {
        case (known, selected, textFilter) =>
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

    private val metricSelected: Observer[MetricKey] = Observer[MetricKey] { key =>
      selectedMetrics.update(cur => if (!cur.contains(key)) cur :+ key else cur)
    }

    private val metricRemoved: Observer[MetricKey] = Observer[MetricKey] { key =>
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
          )
        ),
        div(
          cls := "flex-none",
          div(
            cls := "form-control",
            label(cls := "label", span(cls := "label-text text-xl", "Refresh")),
            input(
              tpe := "text",
              cls := "input input-primary input-bordered",
              value := s"${cfg.refresh.getSeconds}",
              onInput.mapToValue --> curRefresh,
              onMountCallback(_ => curRefresh.set(s"${cfg.refresh.getSeconds.intValue()}"))
            )
          )
        ),
        div(
          cls := "flex-none",
          div(
            cls := "form-control",
            label(cls := "label", span(cls := "label-text text-xl", "Samples")),
            input(
              tpe := "text",
              cls := "input input-primary input-bordered",
              value := s"${cfg.maxSamples}",
              onInput.mapToValue --> curSamples,
              onMountCallback(_ => curSamples.set(s"${cfg.maxSamples}"))
            )
          )
        )
      )

    def render(): HtmlElement =
      div(
        idAttr := dlgId,
        cls := "modal",
        child <-- $cfg.map { cfg =>
          div(
            cls := "modal-box max-w-full h-5/6 mx-12 border-2 flex flex-col bg-accent-focus text-accent-content",
            div(
              cls := "border-b-2",
              span("Panel configuration")
            ),
            div(
              cls := "flex flex-col flex-grow overflow-y-auto",
              configValues(cfg),
              MetricsSelector("Configured metrics", metricRemoved).render(selectedMetrics.signal),
              div(
                cls := "form-control mt-2 flex flex-col",
                label(cls := "label flex-none", span(cls := "label-text text-xl", "Select Metrics to Display")),
                div(
                  cls := "form-control flex flex-row my-2",
                  label(cls := "label flex-none", span(cls := "label-text text-xl", "Metrics filter")),
                  input(
                    tpe := "text",
                    cls := "flex-grow input input-primary input-bordered ml-3",
                    value <-- curFilter,
                    onInput.mapToValue --> curFilter
                  )
                )
              ),
              div(
                cls := "flex-grow",
                div(
                  cls := "max-w-full max-h-full",
                  MetricsSelector("Available Counters", metricSelected, "secondary").render(availableCounters),
                  MetricsSelector("Available Gauges", metricSelected, "secondary").render(availableGauges),
                  MetricsSelector("Available Histograms", metricSelected, "secondary").render(availableHistograms),
                  MetricsSelector("Available Summaries", metricSelected, "secondary").render(availableSummaries),
                  MetricsSelector("Available Set Counts", metricSelected, "secondary").render(availableSetCounts)
                )
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
                    maxSamples = newSamples
                  )
                  Seq(
                    Command.UpdateDashboard(newCfg),
                    Command.ConfigureTimeseries(
                      cfg.id,
                      curTimeseries.filter { case (k, _) => newMetrics.contains(k.metric) }
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
