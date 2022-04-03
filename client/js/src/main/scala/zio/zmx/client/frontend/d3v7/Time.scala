package zio.zmx.client.frontend.d3v7

import scala.scalajs.js.annotation._

import scalajs.js

// https://github.com/d3/d3-time

@JSImport("d3-time", JSImport.Namespace)
@js.native
object d3Time extends js.Object {
  def timeMillisecond: Interval = js.native
  def utcMillisecond: Interval  = js.native
  def timeSecond: Interval      = js.native
  def utcSecond: Interval       = js.native
  def timeMinute: Interval      = js.native
  def utcMinute: Interval       = js.native
  def timeHour: Interval        = js.native
  def utcHour: Interval         = js.native
  def timeDay: Interval         = js.native
  def utcDay: Interval          = js.native
  def timeWeek: Interval        = js.native
  def utcWeek: Interval         = js.native

  def timeSunday: Interval    = js.native
  def utcSunday: Interval     = js.native
  def timeMonday: Interval    = js.native
  def utcMonday: Interval     = js.native
  def timeTuesday: Interval   = js.native
  def utcTuesday: Interval    = js.native
  def timeWednesday: Interval = js.native
  def utcWednesday: Interval  = js.native
  def timeThursday: Interval  = js.native
  def utcThursday: Interval   = js.native
  def timeFriday: Interval    = js.native
  def utcFriday: Interval     = js.native
  def timeSaturday: Interval  = js.native
  def utcSaturday: Interval   = js.native

  def timeMonth: Interval = js.native
  def utcMonth: Interval  = js.native
  def timeYear: Interval  = js.native
  def utcYear: Interval   = js.native

  @js.native
  trait Interval extends js.Object {
    def apply(d: js.Date): js.Date                                            = js.native
    def floor(d: js.Date): js.Date                                            = js.native
    def round(d: js.Date): js.Date                                            = js.native
    def ceil(d: js.Date): js.Date                                             = js.native
    def offset(date: js.Date): js.Date                                        = js.native
    def offset(date: js.Date, step: Double): js.Date                          = js.native
    def range(start: js.Date, stop: js.Date): js.Array[js.Date]               = js.native
    def range(start: js.Date, stop: js.Date, step: Double): js.Array[js.Date] = js.native
  }
}
