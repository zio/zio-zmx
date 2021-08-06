package zio.zmx.client.frontend.views

import scalajs.js
import scalajs.js.annotation.JSImport
import java.util.Date
import java.text.SimpleDateFormat
import zio.duration._
import scalajs.js.JSConverters._

object ScalaDateAdapter {

  sealed trait TimeUnit {
    def name: String
    def duration: Duration
    def fmt: SimpleDateFormat
  }

  object TimeUnit {

    final case object DateTime extends TimeUnit {
      val name: String          = "datetime"
      val duration: Duration    = 1.millis
      val fmt: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss.SSS")
    }

    final case object MilliSecond extends TimeUnit {
      val name: String          = "millisecond"
      val duration: Duration    = 1.millis
      val fmt: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss.SSS")
    }

    final case object Second extends TimeUnit {
      val name: String          = "second"
      val duration: Duration    = 1.second
      val fmt: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss")
    }

    final case object Minute extends TimeUnit {
      val name: String          = "minute"
      val duration: Duration    = 1.minute
      val fmt: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH:mm")
    }

    final case object Hour extends TimeUnit {
      val name: String          = "hour"
      val duration: Duration    = 1.hour
      val fmt: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd-HH")
    }

    final case object Day extends TimeUnit {
      val name: String          = "day"
      val duration: Duration    = 1.day
      val fmt: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    }

    final case object Week extends TimeUnit {
      val name: String          = "week"
      val duration: Duration    = Day.duration * 7
      val fmt: SimpleDateFormat = new SimpleDateFormat("yyyy-ww")
    }

    final case object Month extends TimeUnit {
      val name: String          = "month"
      val duration: Duration    = Day.duration * 30
      val fmt: SimpleDateFormat = new SimpleDateFormat("yyyy-MM")
    }

    final case object Quarter extends TimeUnit {
      val name                  = "quarter"
      val duration              = Month.duration * 3
      val fmt: SimpleDateFormat = Month.fmt
    }

    final case object Year extends TimeUnit {
      val name                  = "year"
      val duration              = Month.duration * 12
      val fmt: SimpleDateFormat = new SimpleDateFormat("yyyy")
    }

    val units: Map[String, TimeUnit] = Map(
      DateTime.name    -> DateTime,
      MilliSecond.name -> MilliSecond,
      Second.name      -> Second,
      Minute.name      -> Minute,
      Hour.name        -> Hour,
      Day.name         -> Day,
      Week.name        -> Week,
      Month.name       -> Month,
      Quarter.name     -> Quarter,
      Year.name        -> Year
    )

    def fromString(s: String): Option[TimeUnit] = units.get(s)
  }

  object ScalaDateAdapter {

    /**
     * Returns a map of time formats for the supported formatting units defined
     * in Unit as well as 'datetime' representing a detailed date/time string.
     * @return {{string: string}}
     */
    val formats: () => js.Dynamic = () => {
      //println(s"Date adapter formats called")
      TimeUnit.units.view.mapValues(_.fmt.toPattern()).toMap.toJSDictionary.asInstanceOf[js.Dynamic]
    }

    /**
     * TODO
     * Parses the given `value` and return the associated timestamp.
     * @param {any} value - the value to parse (usually comes from the data)
     * @param {string} [format] - the expected data format
     */
    val parse: (js.Any, Option[js.Any]) => Double = (v, f) => {
      println(s"Date adapter parse called with ([$v], [$f])")
      0d
    }

    /**
     * Returns the formatted date in the specified `format` for a given `timestamp`.
     * @param {number} timestamp - the timestamp to format
     * @param {string} format - the date/time token
     * @return {string}
     */
    val format: (Double, String) => String = (v, f) => {
      val t   = new Date(v.toLong)
      val sdf = new SimpleDateFormat(f)
      val res = sdf.format(t)
      //println(s"Date adapter format called with ([$v], [$f]) : [$res]")
      res
    }

    /**
     * Adds the specified `amount` of `unit` to the given `timestamp`.
     * @param {number} timestamp - the input timestamp
     * @param {number} amount - the amount to add
     * @param {Unit} unit - the unit as string
     * @return {number}
     */
    val add: (Double, Double, String) => Double = (t, a, u) => {
      val res = TimeUnit.fromString(u).map(unit => t + a * unit.duration.toMillis()) match {
        case Some(value) => value
        case None        => t
      }
      //println(s"Date adapter add called with ([$t], [$a], [$u]), res : [$res]")
      res
    }

    /**
     * Returns the number of `unit` between the given timestamps.
     * @param {number} a - the input timestamp (reference)
     * @param {number} b - the timestamp to subtract
     * @param {Unit} unit - the unit as string
     * @return {number}
     */
    val diff: (Double, Double, String) => Double = (a, b, u) => {
      val res = TimeUnit.fromString(u).map(unit => (a - b) / unit.duration.toMillis()) match {
        case Some(v) => v.floor
        case None    => a - b
      }

      //println(s"Date adapter diff called with ([$a], [$b], [$u])")
      res
    }

    /**
     * Returns start of `unit` for the given `timestamp`.
     * @param {number} timestamp - the input timestamp
     * @param {Unit|'isoWeek'} unit - the unit as string
     * @param {number} [weekday] - the ISO day of the week with 1 being Monday
     * and 7 being Sunday (only needed if param *unit* is `isoWeek`).
     * @return {number}
     */
    val startOf: (Double, String, js.Any) => Double = (t, u, d) => {
      val res =
        TimeUnit.fromString(u).map(unit => (t / unit.duration.toMillis()).floor * unit.duration.toMillis()) match {
          case Some(value) => value
          case None        => t
        }
      //println(s"Date adapter startOf called with ([$t], [$u], [$d]) : [$res]")
      res
    }

    /**
     * Returns end of `unit` for the given `timestamp`.
     * @param {number} timestamp - the input timestamp
     * @param {Unit|'isoWeek'} unit - the unit as string
     * @return {number}
     */
    val endOf: (Double, String) => Double = (t, u) => {
      val res = TimeUnit.fromString(u).map(unit => startOf(t, u, 0d) + unit.duration.toMillis()) match {
        case Some(value) => value
        case None        => t
      }
      //println(s"Date adapter endOf called with ([$t], [$u]) : [$res]")
      res
    }
  }

  @js.native
  @JSImport("chart.js", "_adapters")
  private val _adapters: js.Dynamic = js.native

  def install(): Unit = {
    val _ = _adapters
      .selectDynamic("_date")
      .applyDynamic("override")(
        js.Dynamic.literal(
          "formats" -> ScalaDateAdapter.formats,
          "parse"   -> ScalaDateAdapter.parse,
          "format"  -> ScalaDateAdapter.format,
          "add"     -> ScalaDateAdapter.add,
          "diff"    -> ScalaDateAdapter.diff,
          "startOf" -> ScalaDateAdapter.startOf,
          "endOf"   -> ScalaDateAdapter.endOf
        )
      )
  }

}
