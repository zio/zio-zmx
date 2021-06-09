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

Alle metrics in ZMX are defined in form of aspects that can be applied to effects without changing 
the signature of the effect it is applied to. 

Also MetricAspects are further qualified by a type parameter `A` that must be compatible with the 
output type of the effect. Practically this means that a `MetricAspect[Any]` can be applied to 
any effect while a `MetricAspect[Double]` can only be applied to effects producing a `Double`.

Finally, each metric understands a certain data type it can observe to manipulate it´s state. 
Counters, Gauges, Histograms and Summaries all understand `Double` values while a Set understands 
`String` values. 

In cases where the output type of an effect is not compatible with the type required to manipulate the 
metric, the API defines a `xxxxWith` method to construct a `MetricAspect[A]` with a mapper function 
from `A` to the type required by the metric.

The API function in this document are implemented in the `MetricAspect` object. An effect can be applied to an effect with the `@@` operator. 


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

Now the counter can be applied to any effect. Note, that the same aspect can be applied 
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

Now we can apply it to effects producing `Double` (in a real application the value might be 
the number of bytes read from a stream or something similar):

```scala mdoc:silent
val countBytes = nextDoubleBetween(0.0d, 100.0d) @@ aspCountBytes
```

## Gauges

A gauge in ZMX is a named variable of type `Double` that can change over time. It can either be set 
to an absolute value or relative to the current value.

### API 

Create a gauge that can be set to absolute values, it can be applied to effects yielding a Double

```scala
def setGauge(name: String, tags: Label*): MetricAspect[Double]
```

```scala
def setGaugeWith[A](name: String, tags: Label*)(f: A => Double): MetricAspect[A]
```

```scala
def adjustGauge(name: String, tags: Label*): MetricAspect[Double]
```

```scala
def adjustGaugeWith[A](name: String, tags: Label*)(f: A => Double): MetricAspect[A]
```

### Examples 

Create a gauge that can be set to absolute values, it can be applied to effects yielding a Double

```scala mdoc:silent
val aspGaugeAbs = MetricAspect.setGauge("setGauge")
```

Create a gauge that can be set relative to it's current value, it can be applied to effects 
yielding a Double

```scala mdoc:silent
val aspGaugeRel = MetricAspect.adjustGauge("adjustGauge")
```

Now we can apply these effects to effects having an output type `Double`. Note that we can instrument 
an effect with any number of aspects if the type constraints are satisfied.

```scala mdoc:silent
private lazy val gaugeSomething = for {
  _ <- nextDoubleBetween(0.0d, 100.0d) @@ aspGaugeAbs @@ aspCountAll
  _ <- nextDoubleBetween(-50d, 50d) @@ aspGaugeRel @@ aspCountAll
} yield ()
```

## Histograms

Describe Histograms ... 

### API 

```scala
def observeHistogram(name: String, boundaries: Chunk[Double], tags: Label*): MetricAspect[Double]
```

```scala
def observeHistogramWith[A](name: String, boundaries: Chunk[Double], tags: Label*)(
```

### Examples

```scala mdoc:silent
// Create a histogram with 12 buckets: 0..100 in steps of 10, Infinite
// It also can be applied to effects yielding a Double
val aspHistogram =
  MetricAspect.observeHistogram("zmxHistogram", DoubleHistogramBuckets.linear(0.0d, 10.0d, 11).boundaries)
```

## Summaries

Describe Summaries ... 

### API 

```scala
/**
   * A metric aspect that adds a value to a summary each time the effect it is
   * applied to succeeds.
   */
  def observeSummary(
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: Label*
  ): MetricAspect[Double]

  /**
   * A metric aspect that adds a value to a summary each time the effect it is
   * applied to succeeds, using the specified function to transform the value
   * returned by the effect to the value to add to the summary.
   */
  def observeSummaryWith[A](
    name: String,
    maxAge: Duration,
    maxSize: Int,
    error: Double,
    quantiles: Chunk[Double],
    tags: Label*
  )(f: A => Double): MetricAspect[A]
```  

### Examples

```scala mdoc:silent
// Create a summary that can hold 100 samples, the max age of the samples is 1 day.
// The summary should report th 10%, 50% and 90% Quantile
// It can be applied to effects yielding an Int
val aspSummary =
  MetricAspect.observeSummaryWith[Int]("mySummary", 1.day, 100, 0.03d, Chunk(0.1, 0.5, 0.9))(_.toDouble)
```

## Sets

Describe Sets ...

### API

```scala
/**
 * A metric aspect that counts the number of occurrences of each distinct
 * value returned by the effect it is applied to.
 */
def occurrences(name: String, setTag: String, tags: Label*): MetricAspect[String]

/**
 * A metric aspect that counts the number of occurrences of each distinct
 * value returned by the effect it is applied to, using the specified
 * function to transform the value returned by the effect to the value to
 * count the occurrences of.
 */
def occurrencesWith[A](name: String, setTag: String, tags: Label*)(
  f: A => String
): MetricAspect[A]
```

### Examples

```scala mdoc:silent
  // Create a Set to observe the occurrences of unique Strings
  // It can be applied to effects yielding a String
  val aspSet = MetricAspect.occurrences("mySet", "token")
```  







```scala mdoc:invisible


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
