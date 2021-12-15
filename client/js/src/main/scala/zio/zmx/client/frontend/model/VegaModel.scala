package zio.zmx.client.frontend.model

import scalajs.js
import scalajs.js.JSConverters._

import zio.Chunk

import zio.zmx.client.frontend.state.AppState
import PanelConfig.DisplayConfig

final case class VegaModel(
  cfg: DisplayConfig,
  data: LineChartModel
) {

  private val vegaSchema  = "https://vega.github.io/schema/vega-lite/v5.json"
  private val vegaPadding = 5

  private def labelRef(s: String): String = {
    val allowed: Char => Boolean = c => c.isLetterOrDigit

    val sb = new StringBuilder()
    s.foreach(c => if (allowed(c)) sb.addOne(c) else sb.addOne('_'))

    sb.toString()
  }

  private val toData: TimeSeriesEntry => js.Dynamic = e =>
    js.Dynamic.literal(
      "label"     -> e.key.key,
      "labelRef"  -> labelRef(e.key.key),
      "timestamp" -> e.when,
      "value"     -> e.value
    )

  private def labels(entries: Iterable[TimeSeriesEntry]): js.Dictionary[String] = {
    val props = entries.toSeq.distinct.map(e => (labelRef(e.key.key), e.key.key))
    js.Dictionary[String](props: _*)
  }

  private def colors(cfg: DisplayConfig): js.Dynamic = {
    val tsConfigs = AppState.timeSeries
      .now()
      .getOrElse(cfg.id, Map.empty)
      .values
      .foldLeft[(Chunk[String], Chunk[String])]((Chunk.empty, Chunk.empty)) { case ((labels, colors), e) =>
        (labels :+ e.key.key, colors :+ e.color.toHex)
      }

    js.Dynamic.literal(
      "range" -> tsConfigs._2.toJSArray
    )
  }

  private def replaceData(vega: js.Dynamic, data: js.Array[js.Dynamic]): js.Dynamic = {
    vega.selectDynamic("data").updateDynamic("values")(data)
    vega
  }

  private def createVega(
    cfg: DisplayConfig,
    entries: Iterable[TimeSeriesEntry]
  ): js.Dynamic =
    js.Dynamic.literal(
      "$schema"  -> vegaSchema,
      "width"    -> "container",
      "height"   -> "container",
      "padding"  -> vegaPadding,
      "params"   -> js.Array(
        js.Dynamic.literal("name" -> "labels", "value" -> labels(entries))
      ),
      "mark"     -> js.Dynamic.literal(
        "type"        -> "line",
        "interpolate" -> "monotone",
        "tooltip"     -> true,
        "point"       -> js.Dynamic.literal(
          "filled" -> false,
          "fill"   -> "white"
        )
      ),
      "data"     -> js.Dynamic.literal(
        "values" -> null
      ),
      "encoding" -> js.Dynamic.literal(
        "x"     -> js.Dynamic.literal(
          "field" -> "timestamp",
          "type"  -> "temporal",
          "title" -> "T"
        ),
        "y"     -> js.Dynamic.literal(
          "field" -> "value",
          "type"  -> "quantitative",
          "title" -> "V"
        ),
        "color" -> js.Dynamic.literal(
          "field"  -> "labelRef",
          "type"   -> "nominal",
          "scale"  -> colors(cfg),
          "legend" -> js.Dynamic.literal(
            "title"     -> null,
            "labelExpr" -> "labels[datum.value]"
          )
        )
      )
    )

  val vegaDef: js.Dynamic = {
    val entries = data.data.values.flatten

    val vegaData = entries.map(toData).toJSArray

    val rawVega = cfg.vegaConfig match {
      case None    =>
        createVega(cfg, entries)
      case Some(v) =>
        v
    }

    replaceData(rawVega, vegaData)
  }

  val vegaDefJson = js.JSON.stringify(vegaDef, (s: String, v: js.Any) => v, 2)
}
