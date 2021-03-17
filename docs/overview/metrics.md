---
id: overview_metrics
title: "Metrics"
---
```scala mdoc:invisible
import zio._
import zio.random._
import zio.duration._
import zio.clock._

import zio.zmx.metrics._
```
ZMX allows the instrumentation of ZIO based applications with some extensions to the well known ZIO DSL. Using the DSL generates metrics events which will be processed 
by a reporting backend that is registered as a layer within the application. Currently, reporting to Statsd and Prometheus is supported. It is important to note that 
switching from one reporting backend to another does not require any code changes to the instrumented app. 

## The ZMX metrics DSL 

The ZMX metrics DSL is defined within the `ZMX` object and offers methods to manipulate all of the known metrics. Whenever it makes sense, we have also included 
extensions to the ZIO object to make metric capturing more intuitive.

```scala mdoc:silent
import zio.zmx._

trait InstrumentedSample {

  // Count something explicitly
  private lazy val doSomething =
    incrementCounter("myCounter", 1.0d, "effect" -> "count1")

  // Manipulate an arbitrary Gauge
  private lazy val gaugeSomething = for {
    v1 <- nextDoubleBetween(0.0d, 100.0d)
    v2 <- nextDoubleBetween(-50d, 50d)
    _  <- setGauge("setGauge", v1)             // Will set the gauge to an absolute value 
    _  <- adjustGauge("adjustGauge", v2)    // Will modify an existing gauge using the observed value as delta
  } yield ()

  // Use a convenient extension to count the number of executions of an effect
  // In this particular case count how often `gaugeSomething` has been set
  private lazy val doSomething2 = gaugeSomething.counted("myCounter", "effect" -> "count2")

  def program: ZIO[ZEnv, Nothing, ExitCode] = for {
    _ <- doSomething.schedule(Schedule.spaced(100.millis)).forkDaemon
    _ <- doSomething2.schedule(Schedule.spaced(200.millis)).forkDaemon
  } yield ExitCode.success
}
```

In the example above `doSomething` and `doSomething2` both instrument a given ZIO effect and count the number of executions of that effect. 
`doSomething`does an explicit count while `doSomething2` uses an extension method on `ZIO` itself. The effect counted in `doSomething2`
simulates 2 metrics being measured with a `gauge`. 

Note, that the instrumentation just defines a model of what shall be measured and has no backend specific code whatsoever. Only by providing 
an `Instrumentation` we select what reporting backend will be used. 

---
**NOTE**

We have put the instrumented code in a `trait` for demonstration purposes only to show that the same code can be used to report to 
either backend.

---

