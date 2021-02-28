package zio.zmx.prometheus

import zio.json._
import zio.zmx.prometheus.PMetric.Details

object PrometheusJsonEncoder {

  implicit val quantileEncoder: JsonEncoder[Quantile] =
    DeriveJsonEncoder.gen[Quantile]

  implicit val timeSeriesEncoder: JsonEncoder[TimeSeries] =
    DeriveJsonEncoder.gen[TimeSeries]

  implicit val detailsEncoder: JsonEncoder[Details] =
    DeriveJsonEncoder.gen[Details]

  implicit val pmetricEncoder: JsonEncoder[PMetric] =
    DeriveJsonEncoder.gen[PMetric]
}
