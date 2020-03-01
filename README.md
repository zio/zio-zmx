# ZIO-ZMX

## THIS PROJECT IS A WIP and is NOT in usable state yet - will remove this banner when it is :-) 

| CI | Release | Snapshot | Discord |
| --- | --- | --- | --- |
| [![Build Status][Badge-Circle]][Link-Circle] | [![Release Artifacts][Badge-SonatypeReleases]][Link-SonatypeReleases] | [![Snapshot Artifacts][Badge-SonatypeSnapshots]][Link-SonatypeSnapshots] | [![Badge-Discord]][Link-Discord] |

# Summary
ZIO Monitoring and metrics to aid in diagnosing problems and monitoring your ZIO fibers. This project is inspired by Java `JStack` utility.

See [ZMX Design and Requirements](#ZMX-Design-and-Requirements) for the goals of this project.

# Documentation
[zio.zmx Microsite](https://zio.github.io/zio.zmx/)

# Contributing
[Documentation for contributors](https://zio.github.io/zio.zmx/docs/about/about_contributing)

## Code of Conduct

See the [Code of Conduct](https://zio.github.io/zio.zmx/docs/about/about_coc)

## Support

Come chat with us on [![Badge-Discord]][Link-Discord].


# License
[License](LICENSE)

[Badge-SonatypeReleases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio.zmx_2.12.svg "Sonatype Releases"
[Badge-SonatypeSnapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio.zmx_2.12.svg "Sonatype Snapshots"
[Badge-Discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
[Badge-Circle]: https://circleci.com/gh/zio/zio.zmx.svg?style=svg "circleci"
[Link-Circle]: https://circleci.com/gh/zio/zio.zmx "circleci"
[Link-SonatypeReleases]: https://oss.sonatype.org/content/repositories/releases/dev/zio/zio.zmx_2.12/ "Sonatype Releases"
[Link-SonatypeSnapshots]: https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio.zmx_2.12/ "Sonatype Snapshots"
[Link-Discord]: https://discord.gg/2ccFBr4 "Discord"

# ZMX Design and Requirements


## Overview                                                                                                                                                        
                                                                                                                                                        
                                                                                                                                                        
            ┌─────────────────────┐                               client -> server                            ┌─────────────────────┐                   
            │                     ◀━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫                     │                   
            │     ZMX Server      │                                                                           │     ZMX Client      │                   
            │                     ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━▶                     │                   
            └─────────────────────┘                               server -> client                            └─────────────────────┘                   
                                                                                                                         ┃                              
                      ┃                        (ZmxProtocol which is based on the Redis RESP protocol)                                                  
                                                                                                                         ┃                              
                      ┃                                                                                                                                 
                                                                                                                         ┃                              
                      ┃                                                                                                                                 
                                                                                                                         ▼                              
                      ▼                                                                                                                                 
         ┌─────────────────────────────────────────────────────┐                                 ┌─────────────────────────────────────────────────────┐
         │- Send Command to server                             │                                 │- Send Command to server                             │
         │- User specified port server is to run on            │                                 │- User specified port server is running on           │
         │- Always run on localhost where ZIO is running       │                                 │- Client implementations planned:                    │
         │- Supported Commands Planned:                        │                                 │    - CLI                                            │
         │    - Fiber Dump                                     │                                 │    - Text editor / IDE                              │
         │    - Metrics                                        │                                 └─────────────────────────────────────────────────────┘
         │        - prometheus                                 │                                                                                        
         │    - Test (Just replys with a test message)         │                                                                                        
         └─────────────────────────────────────────────────────┘                                              
         
         We want to give users the option to run a light weight server local to where their ZIO app is running that supports a few commands to aid monitoring and metrics of their application.

### Commands

Commands to support:

- Fiber Dump - Fiber dump of all fibers
- Metrics
    - stdout - prints metrics to stdout
    - prometheus - pushes metrics to prometheus
    - prometheus & other metrics integrations supported by utilizing `zio-metrics`
- Test 
    - Server replies with a test message - used to test server working

### Client

Clients send commands to the server and wait for a response. Initially we are planning to support a CLI tool as the client but do plan to support some level of IDE/Text editor support to aid debugging.

### Protocol

The protocol used to communicate between the client and server is a cut down version of the RESP protocol that Redis uses. For more information on this protocol please see the specification docs: [Redis Protocol specification – Redis](https://redis.io/topics/protocol)

Choosing this protocol enables us to have something simple to implement without any external dependencies being introduced and thereby keeping the client and server lightweight. 

## Post MVP ideas for roadmap

- Ability to send fiber dump and metrics to:
    - Kafka
    - Redis
    - Elasticsearch
- Support the [Debug Adapter Protocol](https://microsoft.github.io/debug-adapter-protocol/) for IDE/Text editor support

## Design questions and decisions to review

- How are we going to enable the user to choose which port the server uses? 
- Any security considerations? 
- Use `zio-metrics` as a dependency or merge the two projects into this repo.

## Project Plan for initial release

[Project Plan and Issues](https://github.com/zio/zio-zmx/projects/1)
