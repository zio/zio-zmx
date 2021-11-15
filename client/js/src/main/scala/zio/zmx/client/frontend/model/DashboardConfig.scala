package zio.zmx.client.frontend.model

final case class DashboardConfig[+T](
  name: String,
  view: T
)
