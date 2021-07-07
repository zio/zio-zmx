package zio.zmx.client.frontend.views

import com.raquo.laminar.api.L._

object GaugeView {

  def create(key: String) = div(key)

  // def create(key: MetricKey.Gauge, $gauge: Signal[GaugeChange]): Div = {
  //   println(s"GAUGE CREATION ${key}")
  //   val _        = $gauge
  //   var minGauge = Double.MaxValue
  //   var maxGauge = Double.MinValue

  //   val $offset: Signal[(Double, Double, Double)] =
  //     $gauge.map { gauge =>
  //       val value = gauge.value
  //       // println(s"VALUE: ${value}")
  //       // println(s"MIN: ${minGauge}")
  //       // println(s"MAX: ${maxGauge}")
  //       minGauge = value min minGauge
  //       maxGauge = value max maxGauge

  //       val offset =
  //         if ((maxGauge - minGauge) == 0) 0
  //         else (value - minGauge) / (maxGauge - minGauge)

  //       (minGauge, offset, maxGauge)
  //     }

  //   div(
  //     div(
  //       strong(key.name)
  //     ),
  //     pre(child.text <-- $offset.map(_._3.toString)),
  //     div(
  //       height("80px"),
  //       width("40px"),
  //       background("#333"),
  //       div(
  //         height("3px"),
  //         width("40px"),
  //         background("blue"),
  //         position.relative,
  //         top <-- $offset.map(_._2 * 77.0).spring.px
  //       )
  //     ),
  //     pre(child.text <-- $gauge.map(_.value.toString)),
  //     pre(child.text <-- $offset.map(_._1.toString))
  //   )
  // }
}
