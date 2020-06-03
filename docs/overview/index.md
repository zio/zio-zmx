---
id: overview_index
title: "Getting Started"
---

 ZIO-ZMX allows developers to observe everything that goes on inside a ZIO app interactively.

Currently, developers must assess the quality of their Software through testing, that is, determining whether the actual effects or outputs of our program match the expected results. This is an example of indirect observation since instead of observing how our program behaves throughout its lifecycle, we conform ourselves with observing the final result. This is especially true, for asynchronous and concurrent applications, where both debugging and reasoning are hard to do correctly and may prove to not be enough.

ZIO-ZMX allows developers to observe everything that goes on inside a ZIO app interactively. Moreover, it provides a `Layer` which external tools can use to extend and further improve the monitoring and debugging experience.

 - **[Metrics](metrics.md)** — ZIO-ZMX metrics provider

## Installation

Include ZIO ZMX in your project by adding the following to your `build.sbt`:

```scala mdoc:passthrough

println(s"""```""")
if (zio.zmx.BuildInfo.isSnapshot)
  println(s"""resolvers += Resolver.sonatypeRepo("snapshots")""")
println(s"""libraryDependencies += "dev.zio" %% "zio-zmx" % "${zio.zmx.BuildInfo.version}"""")
println(s"""```""")

```

