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

Create a counter which is incremented by `1` every time it is executed successfully. This can be applied to any effect. 

```scala
def count(name: String, tags: Label*): MetricAspect[Any]
```  

A counter which counts the number of failed executions of the effect it is applied to. This can 
be applied to any effect. 

```scala
def countErrors(name: String, tags: Label*): MetricAspect[Any]
```

This counter can be applied to effects having an output type of `Double`. The counter will be 
increased by the value the effect produces. 

```scala
def countValue(name: String, tags: Label*): MetricAspect[Double]
```

A counter that can be applied to effects having the result type `A`. Given the effect 
produces `v: A`, the counter will be increased by `f(v)`.
```scala
def countValueWith[A](name: String, tags: Label*)(f: A => Double): MetricAspect[A]
```

### Examples

Create a counter named `countAll` which is incremented by `1` every time it is invoked. 

```scala mdoc:silent
val aspCountAll = MetricAspect.count("countAll")
```

Now, the counter can be applied to any effect. Note, that the same aspect can be applied 
to more than one effect. In the example we would count the sum of executions of both effects 
in the for comprehension. 

```scala mdoc:silent
val countAll = for {
  _ <- ZIO.unit @@ aspCountAll
  _ <- ZIO.unit @@ aspCountAll
} yield ()
```  

Create a counter named `countBytes` that can be applied to effects having the output type `Double`. 

```scala mdoc:silent
val aspCountBytes = MetricAspect.countValue("countBytes")
```

Now, we can apply it to effects producing `Double` (in a real application the value might be 
the number of bytes read from a stream or something similar):

```scala mdoc:silent
val countBytes = nextDoubleBetween(0.0d, 100.0d) @@ aspCountBytes
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
