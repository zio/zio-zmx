package zio.zmx.metrics

trait Tag[Metric] {
  def tag(metric: Metric): Metric
}

object Tag {
  implicit val counter: Tag[Counter] =
    new Tag[Counter] {
      def tag(metric: Counter): Counter =
        ???
    }
}
