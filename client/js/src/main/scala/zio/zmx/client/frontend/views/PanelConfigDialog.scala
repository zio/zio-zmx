package zio.zmx.client.frontend.views

import zio.Chunk
import com.raquo.laminar.api.L._
import zio.zmx.client.frontend.state.Command
import zio.zmx.client.frontend.model.PanelConfig.DisplayConfig
import zio.zmx.client.frontend.model.MetricInfo
import zio.zmx.client.frontend.state.AppState

object PanelConfigDialog {

  def render(cfg: DisplayConfig, id: String): HtmlElement =
    new PanelConfigDialogImpl(cfg, id).render()

  private class PanelConfigDialogImpl(cfg: DisplayConfig, dlgId: String) {

    private val curTitle: Var[String]                   = Var(cfg.title)
    private val selectedMetrics: Var[Chunk[MetricInfo]] = Var(Chunk.empty)

    private val metricSelected: Observer[MetricInfo] = Observer[MetricInfo] { info =>
      selectedMetrics.update(cur => if (!cur.contains(info)) cur :+ info else cur)
    }

    def render(): HtmlElement =
      div(
        idAttr := dlgId,
        cls := "modal",
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
                value <-- curTitle,
                onInput.mapToValue --> curTitle
              )
            ),
            div(
              cls := "form-control",
              label(cls := "label", span(cls := "label-text", "Configured metrics")),
              new MetricsView(Observer.empty).render(selectedMetrics.signal)
            ),
            div(
              cls := "form-control",
              label(cls := "label", span(cls := "label-text", "Available metrics")),
              new MetricsView(metricSelected).render(AppState.counterInfos),
              new MetricsView(metricSelected).render(AppState.gaugeInfos),
              new MetricsView(metricSelected).render(AppState.histogramInfos),
              new MetricsView(metricSelected).render(AppState.summaryInfos),
              new MetricsView(metricSelected).render(AppState.setCountInfos)
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
                val newCfg = cfg.copy(title = curTitle.now())
                Command.UpdateDashboard(newCfg)
              } --> Command.observer,
              "Apply"
            )
          )
        )
      )
  }
}
