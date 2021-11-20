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

    private val curTitle: Var[String]                  = Var("")
    private val selectedMetrics: Var[Chunk[MetricKey]] = Var(Chunk.empty)

    private val metricSelected: Observer[MetricKey] = Observer[MetricKey] { key =>
      selectedMetrics.update(cur => if (!cur.contains(key)) cur :+ key else cur)
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
                label(cls := "label", span(cls := "label-text", "Title")),
                input(
                  tpe := "text",
                  cls := "input input-primary input-bordered",
                  placeholder("Enter Diagram title"),
                  value := cfg.title,
                  onMountCallback(_ => curTitle.set(cfg.title)),
                  onInput.mapToValue --> curTitle
                )
              ),
              div(
                cls := "form-control",
                label(cls := "label", span(cls := "label-text", "Configured metrics")),
                onMountCallback(_ => selectedMetrics.set(cfg.metrics)),
                new MetricsView(Observer.empty).render(selectedMetrics.signal)
              ),
              div(
                cls := "form-control",
                label(cls := "label", span(cls := "label-text", "Available metrics")),
                new MetricsView(metricSelected).render(AppState.knownCounters),
                new MetricsView(metricSelected).render(AppState.knownGauges),
                new MetricsView(metricSelected).render(AppState.knownHistograms),
                new MetricsView(metricSelected).render(AppState.knownSummaries),
                new MetricsView(metricSelected).render(AppState.knownSetCounts)
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
                  val newCfg = cfg.copy(
                    title = curTitle.now(),
                    metrics = selectedMetrics.now()
                  )
                  Command.UpdateDashboard(newCfg)
                } --> Command.observer,
                "Apply"
              )
            )
          )
        }
      )
  }
}
