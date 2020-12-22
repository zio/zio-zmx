---
id: metrics_index
title: "Metrics"
---
ZMX metrics are designed to capture metrics from a running ZIO application using a DSL which feels like a natural extension to the well known
ZIO DSL. Application developers use this DSL to instrument their own methods capturing the mtrics they are interested in. The metrics are captured
in a backend agnostic way as a stream of `MetricEvent`. 

An instrumention in ZIO ZMX is the mapping of metric events to a spacific backend. At the moment ZIO ZMX supports intrumentations for 
[Datadog](https://www.datadoghq.com/) and [Prometheus](https://prometheus.io).

Datadog is an extension to [statsd](https://github.com/statsd/statsd) supporting everything in statsd and some additional metrics and 
augmentary data on top. Reporting to Datadog can be tested by creating a free test account with Datadog and configuring an agent on the 
developers machine used to forward captured metrics to Datadog. 

The Datadog instrumentation will send its metrics directly to the Datadog agent or a statsd instance using UDP datagrams. The protocol is 
implemented within ZMX and does not require additional dependencies. 

Prometheus is an alternative to Datadog / Statsd and is also widely used to monitor application. A prometheus agent is normally configured 
to poll metrics from various sources. These sources are also known as _exporters_ in the prometheus universe. Each exporter provides a HTTP 
endpoint providing the current state of all collected metrics in a [text format](https://prometheus.io/docs/instrumenting/exposition_formats/)
specified by prometheus. 

Very often, prometheus is used in conjunction with [Grafana](https://grafana.com/) to display the collected metrics in dashboards.

The prometheus instrumentation collects and aggregates the metrics from the metric ebent stream and provides a method to produce the report 
according to the prometheus specification. At the moment we do not provide the HTTP endpoint in the ZMX production code, but only show an 
example how the report can be served via a simple HTTP endpoint in the test code. 

## Basics 

The ZMX supported metrics are defined in the package `zio.zmx.metrics`. All metrics are captured as instances of `MetricEvent` shown below. 

```scala
final case class MetricEvent(
  name: String,
  tags: Chunk[Label],
  details: MetricEventDetails
) 
```

Each event has a `name` and a potential empty chunk of `Label`s identifying the metric. A `Label` is simply a key/value pair that can be used
to further qualify the metrics. Within the reporting backends normally an aggregation for metrics with the name can be retrieved, while 
the labels are normally used for a further drill down. 

Within ZMX `name` and `Label`s are used as a composed key to capture and report the metrics. 

Finally, the event carries the actual measurement as an instance of `MetricEventDetails`. Refer to the reminder of this page to learn about supported 
metric types.

## ZMX metrics under the covers 

A ZMX metrics instrumented stream generates a `ZStream[Any, Nothing, TimedMetricEvent]` using an underlying `MetricsChannel`. The channel instance is defined 
within the `ZMX`package object. and will be the same for the entire application. 

---
**NOTE**

We have chosen **not** to implement the metrics channel as a `ZLayer` as it might be expected within a ZIO app. We think it is easier to intrument an existing 
application with ZMX if the the metrics API does not introduce a dependency to a new layer just to take measurements. This approach may change over time - we 
need to see how usable the metrics APi will be. 

---

For convenience we have provided a new trait `ZMXApp` which does the same thing than the well known `ZIO.App`, but requires that an intrumentation is provided 
to the application. An instrumentation in that context is a mapping from `MetricEvent`s to a reporting backend. 

A ZMX instrumentation is defined as:

```scala
trait Instrumentation {
  def report: ZIO[Clock, Nothing, String] = ZIO.succeed("")
  def handleMetric(me: TimedMetricEvent): ZIO[Any, Nothing, Unit]
}
```

Here, the `handleMetric` is responsible for handling a single metric from the generated Stream of mettric events. For example, the statsd implementation 
will send a [datagram](https://docs.datadoghq.com/developers/dogstatsd/datagram_shell/?tab=metrics) to a configured statsd collector. 

```scala
def handleMetric(me: TimedMetricEvent) =
  me.event.details match {
    case c: MetricEventDetails.Count       => send(Metric.Counter(me.event.name, c.v, 1d, me.event.tags))
    case g: MetricEventDetails.GaugeChange =>
      for {
        v <- updateGauge(me.metricKey)(v => if (g.relative) v + g.v else g.v)
        _ <- send(Metric.Gauge(me.event.name, v, me.event.tags))
      } yield ()
    case _                                 => ZIO.unit
  }
```    

The `ZMXApp` uses the provided instrumentation to create a sink for the stream of events:

```scala
val eventSink =
  ZSink.foreach[Any, Nothing, TimedMetricEvent](m => iRef.service.flatMap(inst => inst.handleMetric(m)))
```  

The main method then runs the stream and also takes care to flush any outstanding metrics upon termination. 

## A Note on rates

In statsd/Datadog some metrics can be configured with a `rate`, which can take a value between `0.0` and `1.0`. The rate indicates the percentage of values 
that shall be reported to the backend. For example, a rate of `0.1` would result in 10% of the values to be reported together with the rate of `0.1`. The actual
monitored value will be `value / rate`, so in our example that would be a multiplication by `10`. 

The motivation behind rates is to save resources when a measurement from a high volume metric is taken. 

Prometheus does not support rates, but we have decided to support rates for all the metrics that support them in statsd and perform the adjustment 
of values within the Prometheus instrumentation. 

---
**NOTE**

This still needs to be implemented within the revised package structure. Most likely the implementation will be a clever filtter on the event 
stream, so that any backend reporting effects will only execute for the metrics passing the filter. 

---

## Suported reporting backends 

### StatsD / Datadog 

TODO: explain Datadog specifics / defferences 

### Prometheus 

TODO: explain Prometheus specifics / defferences 

## Metrics supported in ZMX

### Counter 

A `Counter`in ZMX is a monotonically increasing series of values. Even though statsd and Datadog would allow counters to decrease, we enforce that 
counters in ZMX can only increase. As a consequence, a metric that can either increase or decrease should be a gauge. 

TBD: Code example to use a counter 

### Gauge 

A `Gauge` in Zmx is a measurement that can either increase or decrease always reflecting the latest value the was reported.

## Metrics to be defined 

### Histogram

Modeled after `Histograms` in Prometheus, sorts observed values into predefined buckets. 

### Summary

Modeled after `Summary` in Prometheus, keeps observed values over a sliding window of time and supports reporting in terms of predefined quantiles. 
This is very similar to `Histogram` in Datadog. 

### Meter

A Statsd metric very similar to `Gauge`. 

### Timer 

Support for measuring time and keep the observed time spans in histograms or summaries. 

