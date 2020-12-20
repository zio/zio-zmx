package zio.zmx.statsd

import zio.Chunk
import zio.zmx.statsd.StatsdDataModel._
import java.text.DecimalFormat
import java.time.Instant
import zio.zmx.metrics.MetricsDataModel.Label

object StatsdEncoder {

  val format = new DecimalFormat("0.################")

  private def encode(
    name: String,
    value: String,
    sampleRate: Double,
    metricType: String,
    tags: Chunk[Label]
  ): String = {
    val tagString = encodeTags(tags)
    val rate      = if (sampleRate < 1.0) s"|@${format.format(sampleRate)}" else ""
    s"${name}:${value}|${metricType}${rate}${tagString}"
  }

  private def encodeTags(tags: Chunk[Label]): String =
    if (tags.isEmpty) ""
    else tags.map(t => s"${t._1}:${t._2}").mkString("|#", ",", "")

  private def encodeEvent(event: Metric.Event): String = {
    val timestamp   = event.timestamp.fold(s"|d:${Instant.now().toEpochMilli()}")(l => s"|d:$l")
    val hostname    = event.hostname.fold("")(h => s"|h:$h")
    val aggKey      = event.aggregationKey.fold("")(k => s"|k:$k")
    val priority    = event.priority.fold("|p:normal")(s => s"|p:${encodeServiceInfo(s)}")
    val sourceType  = event.sourceTypeName.fold("")(s => s"|s:$s")
    val alertType   = event.alertType.fold("|t:info")(s => s"|t:${encodeServiceInfo(s)}")
    val tagString   = encodeTags(event.tags)
    val encodedText = event.text.replace("\n", "\\\\n")
    s"_e{${event.name.size},${event.text.size}}:${event.name}|${encodedText}$timestamp$hostname$aggKey$priority$sourceType$alertType" + tagString
  }

  private def encodeServiceCheck(serviceCheck: Metric.ServiceCheck): String = {
    val name      = serviceCheck.name
    val status    = serviceCheck.status
    val timestamp = serviceCheck.timestamp.fold(s"|d:${Instant.now().toEpochMilli()}")(l => s"|d:$l")
    val hostname  = serviceCheck.hostname.fold("")(h => s"|h:$h")
    val tagString = encodeTags(serviceCheck.tags)
    val message   = serviceCheck.message.fold("")(m => s"|m:${m.replace("\n", "\\\\n")}")
    s"_sc|$name|${encodeServiceInfo(status)}$timestamp$hostname$tagString$message"
  }

  def encode(metric: Metric[_]): String =
    metric match {
      case Metric.Counter(name, value, sampleRate, tags)   => encode(name, format.format(value), sampleRate, "c", tags)
      case Metric.Gauge(name, value, tags)                 => encode(name, format.format(value), 1.0, "g", tags)
      case Metric.Histogram(name, value, sampleRate, tags) => encode(name, format.format(value), sampleRate, "h", tags)
      case Metric.Meter(name, value, tags)                 => encode(name, format.format(value), 1.0, "m", tags)
      case Metric.Set(name, value, tags)                   => encode(name, format.format(value), 1.0, "s", tags)
      case Metric.Timer(name, value, sampleRate, tags)     => encode(name, format.format(value), sampleRate, "ms", tags)
      case evt: Metric.Event                               => encodeEvent(evt)
      case chk: Metric.ServiceCheck                        => encodeServiceCheck(chk)
      case Metric.Zero                                     => ""
    }

  private def encodeServiceInfo(service: ServiceInfo): String =
    service match {
      case ServiceCheckStatus.Ok       => "0"
      case ServiceCheckStatus.Warning  => "1"
      case ServiceCheckStatus.Critical => "2"
      case ServiceCheckStatus.Unknown  => "3"
      case EventPriority.Low           => "low"
      case EventPriority.Normal        => "normal"
      case EventAlertType.Error        => "error"
      case EventAlertType.Info         => "info"
      case EventAlertType.Warning      => "warning"
      case EventAlertType.Success      => "success"
    }
}
