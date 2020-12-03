package zio.zmx

import zio.Chunk

object MetricsDataModel {
  sealed case class Label(key: String, value: String) {
    override def toString() = s"$key:$value"
  }

  sealed trait ServiceInfo

  sealed trait ServiceCheckStatus extends ServiceInfo

  object ServiceCheckStatus {
    case object Ok       extends ServiceCheckStatus
    case object Warning  extends ServiceCheckStatus
    case object Critical extends ServiceCheckStatus
    case object Unknown  extends ServiceCheckStatus
  }

  sealed trait EventPriority extends ServiceInfo

  object EventPriority {
    case object Low    extends EventPriority
    case object Normal extends EventPriority
  }

  sealed trait EventAlertType extends ServiceInfo

  object EventAlertType {
    case object Error   extends EventAlertType
    case object Info    extends EventAlertType
    case object Success extends EventAlertType
    case object Warning extends EventAlertType
  }

  sealed trait Metric[+A] {
    def name: String

    def value: A

    def tags: Chunk[Label]
  }
  object Metric           {
    sealed case class Counter(name: String, value: Double, sampleRate: Double, tags: Chunk[Label])
        extends Metric[Double]

    sealed case class Event(
      name: String,
      value: String,
      timestamp: Option[Long],
      hostname: Option[String],
      aggregationKey: Option[String],
      priority: Option[EventPriority],
      sourceTypeName: Option[String],
      alertType: Option[EventAlertType],
      tags: Chunk[Label]
    ) extends Metric[String] {
      def text: String = value
    }

    sealed case class Gauge(name: String, value: Double, tags: Chunk[Label]) extends Metric[Double]

    sealed case class Histogram(name: String, value: Double, sampleRate: Double, tags: Chunk[Label])
        extends Metric[Double]

    sealed case class Meter(name: String, value: Double, tags: Chunk[Label]) extends Metric[Double]

    sealed case class ServiceCheck(
      name: String,
      status: ServiceCheckStatus,
      timestamp: Option[Long],
      hostname: Option[String],
      message: Option[String],
      tags: Chunk[Label]
    ) extends Metric[ServiceCheckStatus] {
      def value: ServiceCheckStatus = status
    }

    sealed case class Set(name: String, value: String, tags: Chunk[Label]) extends Metric[String]

    sealed case class Timer(name: String, value: Double, sampleRate: Double, tags: Chunk[Label]) extends Metric[Double]

    case object Zero extends Metric[Int] {
      val name    = ""
      val tags    = Chunk.empty
      val value   = 0
      val isEmpty = true

      def unapply(): Boolean = true
    }
  }
}
