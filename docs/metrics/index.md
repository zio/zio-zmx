---
id: metrics_index
title: "ZMX Metric Reference"
---

```scala mdoc:invisible
import zio._
import zio.random._
import zio.duration._
import zio.zmx.metrics._
import zio.zmx.state.DoubleHistogramBuckets
```

## Counter

A counter in ZMX is simply a named variable that increases over time.

### API 

```scala
def count(name: String, tags: Label*): MetricAspect[Any]
```  

```scala
def countValue(name: String, tags: Label*)
```
  def countValueWith[A](name: String, tags: Label*)(f: A => Double)

  def countErrors(name: String, tags: Label*): MetricAspect[Any]

```scala mdoc:silent
// Create a counter applicable to any effect
val aspCountAll = MetricAspect.count("countAll")
```

## Gauges

```scala mdoc:silent
// Create a gauge that can be set to absolute values, it can be applied to effects yielding a Double
val aspGaugeAbs = MetricAspect.setGauge("setGauge")
// Create a gauge that can be set relative to it's current value, it can be applied to effects yielding a Double
val aspGaugeRel = MetricAspect.adjustGauge("adjustGauge")
```  
  Describe Gauges here ...

## Histograms

```scala mdoc:silent
// Create a histogram with 12 buckets: 0..100 in steps of 10, Infinite
// It also can be applied to effects yielding a Double
val aspHistogram =
  MetricAspect.observeHistogram("zmxHistogram", DoubleHistogramBuckets.linear(0.0d, 10.0d, 11).boundaries)
```

## Summaries

```scala mdoc:silent
// Create a summary that can hold 100 samples, the max age of the samples is 1 day.
// The summary should report th 10%, 50% and 90% Quantile
// It can be applied to effects yielding an Int
val aspSummary =
  MetricAspect.observeSummaryWith[Int]("mySummary", 1.day, 100, 0.03d, Chunk(0.1, 0.5, 0.9))(_.toDouble)
```

- Sets

```scala mdoc:silent
  // Create a Set to observe the occurrences of unique Strings
  // It can be applied to effects yielding a String
  val aspSet = MetricAspect.occurrences("mySet", "token")
```  

```scala mdoc:invisible

  private lazy val gaugeSomething = for {
    _ <- nextDoubleBetween(0.0d, 100.0d) @@ aspGaugeAbs @@ aspCountAll
    _ <- nextDoubleBetween(-50d, 50d) @@ aspGaugeRel @@ aspCountAll
  } yield ()

  // Just record something into a histogram
  private lazy val observeNumbers = for {
    _ <- nextDoubleBetween(0.0d, 120.0d) @@ aspHistogram @@ aspCountAll
    _ <- nextIntBetween(100, 500) @@ aspSummary @@ aspCountAll
  } yield ()

  // Observe Strings in order to capture unique values
  private lazy val observeKey = for {
    _ <- nextIntBetween(10, 20).map(v => s"myKey-$v") @@ aspSet @@ aspCountAll
  } yield ()

  def program: ZIO[ZEnv, Nothing, ExitCode] = for {
    _ <- gaugeSomething.schedule(Schedule.spaced(200.millis)).forkDaemon
    _ <- observeNumbers.schedule(Schedule.spaced(150.millis)).forkDaemon
    _ <- observeKey.schedule(Schedule.spaced(300.millis)).forkDaemon
  } yield ExitCode.success

```

ZMX metrics are designed to capture metrics from a running ZIO application using a DSL which feels like a natural extension to the well known
ZIO DSL. Application developers use this DSL to instrument their own methods capturing the mtrics they are interested in. The metrics are captured
in a backend agnostic way as a stream of `MetricEvent`. 

An instrumention in ZIO ZMX is the mapping of metric events to a specific backend. At the moment ZIO ZMX supports intrumentations for 
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
import zio._
import zio.zmx._
import zio.zmx.metrics._

final case class MetricEvent(
  name: String,
  details: MetricEventDetails,
  timestamp: Option[java.time.Instant],
  tags: Chunk[Label]
) {
  def metricKey: String =
    ???
}
```

Each event has a `name` and a potential empty chunk of `Label`s identifying the metric. A `Label` is simply a key/value pair that can be used
to further qualify the metrics. Within the reporting backends normally an aggregation for metrics with the name can be retrieved, while 
the labels are normally used for a further drill down. 

Within ZMX `name` and `Label`s are used as a composed key to capture and report the metrics. 

Finally, the event carries the actual measurement as an instance of `MetricEventDetails`. Refer to the reminder of this page to 
learn about supported metric types.

ZMX uses smart constructors to instantiate metric events. These normally have a result type of `Option[MetricEvent]`. As a consequence, 
a metric that cannot be constructed will be swallowed silently with no further error reporting. 

An example would be `ZMX.count` being called with a negative value. 

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
trait MetricsReporter {
  def report(event: MetricEvent): UIO[Any]
}
```

Here, the `handleMetric` is responsible for handling a single metric from the generated Stream of mettric events. For example, the statsd implementation 
will send a [datagram](https://docs.datadoghq.com/developers/dogstatsd/datagram_shell/?tab=metrics) to a configured statsd collector. 

```scala
import zio.zmx.statsd.StatsdDataModel._

def report(event: MetricEvent): UIO[Any] =
  event.details match {
    case c: MetricEventDetails.Count       => send(Metric.Counter(event.name, c.v, 1d, event.tags))
    case g: MetricEventDetails.GaugeChange =>
      for {
        v <- updateGauge(event.metricKey)(v => if (g.relative) v + g.v else g.v)
        _ <- send(Metric.Gauge(event.name, v, event.tags))
      } yield ()
    case _                                 => ZIO.unit
  }

private def send(metric: Metric[Any]): UIO[Unit] =
  ???

private def updateGauge(key: String)(f: Double => Double): ZIO[Any, Nothing, Double] =
  ???
```    

The `ZMXApp` uses the provided instrumentation to create a sink for the stream of events:

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

For now, ZMX supports a subset of metrics that can be found in [Prometheus](https://prometheus.io/) or [StatsD](https://github.com/statsd/statsd) / [Datadog](https://www.datadoghq.com/). Even though the different backends support a similar set of metrics, threr are some subtle 
differences, ZMX user should be aware of. 

However, in both cases we are recording statistical data which is updated in regular intervals. Further, within the visualization we see 
aggregated data. To break down information to a business id like a transaction identifier, neither StatsD nor Prometheus is the right choice. 

Note, that both - StatsD / DataDog and Prometheus / Grafana - provide more functionality than in the following sections. We are just 
summarizing the functionality we are currently leveraging in ZMX. Over ther course of time ZMX might support more of the backend 
functionality, but the design goal of having a unified reporting API remains. 

### StatsD / Datadog 

Within a statsd monitored environment we will normally find at least one _statsd_ collector. This can be an agent installed on one of the 
machines or a server side component providing the same services. 

StatsD instrumented applications send datagrams to the collector, normally using an UDP based protocol. The collector then aggregates the 
data according to a user defined configuration and reports the aggregated data in regular intervals to a configured backend, which is 
responsible for aggregating the data over all connected agents and provide visualization services. 

In our examples we are using a statsd collector residing in a docker image which uses afree Datadog account for visualization. This image
exposes a unix socket which local applications or other docker images can leverage to send their datagrams to. The collector is configured 
to report it's data every 10 seconds to a free account on Datadog. 

As a consequence of the general setup of a statsd environment, visualization will be updated in the intervals the connected agents 
send their data. The instrumented applications send 
their data according to their own requirements. Within a statsd instrumented application there is no need to keep metrics within the 
application for further aggregation. These aggregations happen within the agent. 

Look [here](statsd.md) for a more detailled description of the ZMX StatsD support.

---
**NOTE**

There is one exception in ZIO ZMX per design choice: We do allow gauges to change relative to the previous value. Therefore we are keeping 
track of the last values recorded for all of the Gauges also for the statsd backend.

---

### Prometheus 

Within a typical prometheus environment we also find at least one component collecting the data, the prometheus server. The prometheus 
server provides more functionality than the statsd collector. It organizes its data, so that it can be queried using the [_Prometheus 
Query Language_](https://prometheus.io/docs/prometheus/latest/querying/basics/). 

However, a typical setup is that one or more Prometheus servers are configured as a datasource within a [Grafana](https://prometheus.io/docs/prometheus/latest/querying/basics/) server for further aggregation and visualization. 

A single prometheus server normally collects its data from configured (HTTP) endpoints, each of which provides the collected metrics in a 
special [text format](https://prometheus.io/docs/instrumenting/exposition_formats/). There are many exporters within the prometheus 
ecosystem, but most of them map some application specific metrics to a prometheus compatible endpoint via HTTP. 

--- 
**NOTE**

ZMX provides the Prometheus data using the `report` effect in the `Instrumentation`trait, but does not make any assumptions how that 
data shall be offered via HTTP. Users have to use the HTTP stack of their choice to provide the HTTP endpoint to Prometheus. 

---

Within prometheus the application is responsible for keeping track of the collected metrics and provide the data to prometheus upon request. 

As a consequence, the Prometheus implementation uses a data registry underneath, which is modified by the stream of `MetricEvent`s and 
provides the input to generate the prometheus data. 

Look [here](prometheus.md) for a more detailled description of the ZMX Prometheus support.

## Metrics supported in ZMX

### Counter 

A `Counter`in ZMX is a monotonically increasing series of values. Even though statsd and Datadog would allow counters to decrease, 
we enforce that counters in ZMX can only increase. As a consequence, a metric that can either increase or decrease should be a gauge. 

The easiest way in count something is with the `count` method of the `ZMX` object:

```scala 
lazy val delta: Double =
  ???

// Increase the counter 'myCounter', tagged with `effect -> count1` with some 
// delta, which is a non-negative double. 
private lazy val doSomething: UIO[Any] =
  incrementCounter("myCounter", delta, "effect" -> "count1")

```

Alternatively, we could use an extension on the `ZIO` object to count the number of executions of an effect: 

```scala 
private lazy val myEffect = ZIO.unit
private lazy val doSomething2 = myEffect.counted("myCounter", "effect" -> "count2")
```

### Gauge 

A `Gauge` in Zmx is a measurement that can either increase or decrease always reflecting the latest value the was reported. 
We have chosen to support relative gauge changes for all supported backends.

```scala 
import zio.random._

private lazy val gaugeSomething = for {
  v1 <- nextDoubleBetween(0.0d, 100.0d)
  v2 <- nextDoubleBetween(-50d, 50d)
  _  <- setGauge("setGauge", v1)            // Set the gauge to an absolute value 
  _  <- adjustGauge("adjustgauage", v2)   // Change the gauge with a delta
} yield ()
```

Note that the stats datagrams will always reflect the current absolute value of the gauge.

## Metrics to be defined 

The following metrics shall are currently on the TODO list to be supported in ZMX. The data models for the backends are 
already implemented. The missing part is the definition of the unified API and the mapping of that API to the backends.

### Histogram

Modeled after `Histograms` in Prometheus, sorts observed values into predefined buckets. 

### Summary

Modeled after `Summary` in Prometheus, keeps observed values over a sliding window of time and supports reporting in terms of predefined quantiles. 
This is very similar to `Histogram` in Datadog. 

### Meter

A Statsd metric very similar to `Gauge`. 

### Timer 

Support for measuring time and keep the observed time spans in histograms or summaries. 

