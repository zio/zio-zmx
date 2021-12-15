# ZIO ZMX

| Project Stage | CI | Release | Snapshot | Discord |
| --- | --- | --- | --- | --- |
| [![Project stage][Stage]][Stage-Page] | ![CI][Badge-CI] | [![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases] | [![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots] | [![Badge-Discord]][Link-Discord] |

# Summary

ZIO ZMX allows you to add diagnostics and metrics to any ZIO application.

ZIO ZMX features:

* **Easy Setup** - Add to any ZIO application with only a few lines of code.
* **Diagnostics** - Real time diagnostics on your application as it is running including fiber lifetimes and reasons for termination.
* **Metrics** - Easy, higher performance tracking of arbitrary user defined metrics.
* **Backends** - Support for major metrics collection services including Prometheus and StatsD.
* **Zero Dependencies** - No dependencies other than ZIO itself.

See the microsite for more information.

## ZMX in ZIO 1.x and ZIO 2.x

The API to capture metrics has moved into ZIO core for ZIO 2.x and later. Therefore ZMX 2.x 
concentrates on providing the backend connectivity to report the captured metrics. The design 
goal to have the same instrumentation for all backends remains unchanged. 

From ZMX onwards we will provide a simple dashboard, so that ZIO developers can view the metrics 
captured in their applications in a web browser without setting up one of the more elaborate backends. 

# Documentation
[ZIO ZMX Microsite](https://zio.github.io/zio-zmx/)

# Contributing
[Documentation for contributors](https://zio.github.io/zio-zmx/docs/about/about_contributing)

## Code of Conduct

See the [Code of Conduct](https://zio.github.io/zio-zmx/docs/about/about_coc)

## Support

Come chat with us on [![Badge-Discord]][Link-Discord].


# License
[License](LICENSE)

[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-zmx_2.12.svg "Sonatype Releases"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-zmx_2.12.svg "Sonatype Snapshots"
[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-zmx_2.12/ "Sonatype Releases"
[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-zmx_2.12/ "Sonatype Snapshots"
[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"
[Badge-CI]: https://github.com/zio/zio-zmx/workflows/CI/badge.svg
[Stage]: https://img.shields.io/badge/Project%20Stage-Development-yellowgreen.svg
[Stage-Page]: https://github.com/zio/zio/wiki/Project-Stages

