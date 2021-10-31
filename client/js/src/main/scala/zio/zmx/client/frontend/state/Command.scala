package zio.zmx.client.frontend.state

import zio.Chunk
import com.raquo.airstream.core.Observer
import zio.zmx.client.frontend.model.DiagramConfig

sealed trait Command

object Command {

  case object Disconnect                          extends Command
  final case class Connect(url: String)           extends Command
  final case class AddDiagram(cfg: DiagramConfig) extends Command

  val observer = Observer[Command] {
    case Disconnect =>
      println("Disconnecting from server")
      AppState.shouldConnect.set(false)
      AppState.dashboardConfig.update(_.copy(diagrams = Chunk.empty))

    case Connect(url) =>
      println(s"Connecting url to : [$url]")
      AppState.shouldConnect.set(true)
      AppState.dashboardConfig.update(_.copy(connectUrl = url))

    case AddDiagram(d) => AppState.dashboardConfig.update(cfg => cfg.copy(diagrams = cfg.diagrams :+ d))

  }
}
