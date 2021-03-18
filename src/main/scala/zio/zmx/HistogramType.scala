package zio.zmx

sealed trait HistogramType

object HistogramType {
  case object Histogram extends HistogramType
  case object Summary   extends HistogramType
}
