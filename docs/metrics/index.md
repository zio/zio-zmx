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

## Metrics supported in ZMX

The ZMX supported metrics are defined in the package `zio.zmx.metrics`. All metrics are captured as instances of `MetricEvent` shown below. 

```scala
final case class MetricEvent(
  name: String,
  description: String,
  tags: Chunk[Label],
  details: MetricEventDetails
) {
  def describe(s: String) = copy(description = s)
  def withLabel(l: Label) = copy(tags = tags.filterNot(_.key == l.key) ++ Chunk(l))
}
```

Each event has a `name` and a potential empty chunk of `tags` identifying the metric. A `tag` is simply a key/value pair that can be used
to further qualify the metrics. Within the reporting backends normally an aggregation for metrics with the name can be retrieved, while 
the tags are normally used for a further drill down. 

Within ZMX `name` and `tags` are used as a composed key to capture and report the metrics. 

Furthermore, each event has a description. This description can be sent to backends, so that they can use it as additional information 
on their user interface and dashboards.

Finally, the event carries the actual metric as an instance of `MetricEventDetails`. Refer to the reminder of this page to learn about supported 
metric types.

## A Note on rates

In statsd/Datadog some metrics can be configured with a `rate`, which can take a value between `0.0` and `1.0`. The rate indicates the percentage of values 
that shall be reported to the backend. For example, a rate of `0.1` would result in 10% of the values to be reported together with the rate of `0.1`. The actual
monitored value will be `value / rate`, so in our example that would be a multiplication by `10`. 

The motivation behind rates is to save resources when a measurement from a high volume metric is taken. 

Prometheus doe snot support rates, but we have decided to support rates for all the metrics that support them in statsd and perform the adjustment 
of values within the Prometheus instrumentation. 

### Counter 

A `Counter`in ZMX is a monotonically increasing series of values. Even though statsd and Datadog would allow counters to decrease, we enforce that 
counters in ZMX can only increase. As a consequence, a metric that can either increase or decrease should be a gauge. 

TBD: Code example to use a counter 

Also see:

* [Counter mapping to StatsD](mappings.md#statsd-counter)
* [Counter mapping to Prometheus](mappings.md#prometheus-counter)

### Gauge 

A `Gauge` in Zmx is a measurement that can either increase or decrease always reflecting the latest value the was reported.

### Timer 

TBD
