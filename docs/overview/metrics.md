---
id: overview_metrics
title: "Metrics"
---
ZIO ZMX enables the instrumentation of any ZIO based application with specialized aspects. The type of the original ZIO effect will not change by adding on or more aspects to it. 

Whenever an instrumented effect executes, all of the aspects will be executed as well and each of the 
aspects will capture some data of interest and update some ZMX internal state. Which data will be captured and how it can be used later on is dependant on the metric type associated with the aspect. 

Metrics are normally captured to be displayed in an application like [Grafana](https://grafana.com/) or a cloud based platform like [DatadogHQ](https://docs.datadoghq.com/) or [NewRelic](https://newrelic.com). 
In order to support such a range of different platforms, the metric state is kept in an internal data structure optimized to update the state as efficiently as possible and the data required by one or more of 
the platforms is generated only when it is required. 

This also allows us to provide a ZMX web client (in one of the next minor releases) out of the box to visualize the metrics in the development phase before the decision for a metric platform has been 
made or in cases when the platform might not be feasible to use in development. 

For a sneak preview, building a ZMX metrics client was the topic of two live coding sessions on Twitch TV: 
Building a ZMX metrics client [Part I](https://www.twitch.tv/kitlangton/video/1038831171) and [Part II](https://www.twitch.tv/kitlangton/video/1038926026)

> Changing the targeted reporting back end will have no impact on the application at all. Once instrumented properly, that reporting back decision will happen __at the end of the world__
> in the ZIP applications mainline by injecting one or more of the available reporting clients.

Currently ZMX provides mappings to [StatsD](https://docs.datadoghq.com/) and [Prometheus](https://prometheus.io/) out of the box. 

## General Metric architecture in 

All metrics in ZMX have a name of type `String` which may be augmented by zero or many `Label`s. A `Label` is simply a key/value pair to further qualify the name. 
The distinction is made, because some reporting platforms support tags as well and provide certain aggregation mechanisms for them. 

> For example, think of a counter that simply counts how often a particular service has been invoked. If the application is deployed across several hosts, we might 
> model our counter with a name `myService`and an additional label `(host, ${hostname})`. With such a definition we would see the number of executions for each host, 
> but we could also create a query in Grafana or DatadogHQ to visualize the aggregated value over all hosts. Using more than one label would allow to create visualizations 
> across any combination of the labels. 

An important aspect of metric aspects is that they _understand_ values of a certain type. For example, a Gauge understands double values to manipulate the current 
value within the gauge. This implies, that for effects `ZIO[R, E, A]` where `A` can not be assigned to a `Double` we have to provide a mapping function `A => Double`
so that we can derive the measured value from the effect´s result. 

Finally, more complex metrics might require additional information to specify them completely. For example, within a [histogram](../metrics/index.md#histograms) we need to specify the 
buckets the observed values shall be counted in. 

Please refer to the [Metrics Reference](../metrics/index.md) for more information on the metrics currently supported.
