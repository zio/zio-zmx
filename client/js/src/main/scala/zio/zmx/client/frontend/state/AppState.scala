package zio.zmx.client.frontend.state

import zio._

import com.raquo.laminar.api.L._

import zio.zmx.client.frontend.model._

object AppState {

  val connected: Var[Boolean]     = Var(false)
  val shouldConnect: Var[Boolean] = Var(true)

  val dashboardConfig: Var[DashBoardConfig] = Var(DashBoardConfig("ws://localhost:8080/ws", Chunk.empty))

  def addDiagram(diagram: DiagramConfig) =
    dashboardConfig.update(cfg => cfg.copy(diagrams = cfg.diagrams :+ diagram))

}
