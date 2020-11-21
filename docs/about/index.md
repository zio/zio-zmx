---
id: about_index
title:  "About ZIO ZMX"
---

# Monitoring, Metrics and Diagnostics for ZIO

<pre>                                                                                                                                                        
    ┌─────────────────────┐           client -> server           ┌─────────────────────┐                   
    │                     ◀━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫                     │                   
    │     ZMX Server      │                                      │     ZMX Client      │                   
    │                     ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━▶                     │                   
    └─────────────────────┘           server -> client           └─────────────────────┘                   
              ┃                                                             ┃                              
                               (ZmxProtocol which is based                             
              ┃                 on the Redis RESP protocol)                 ┃                              
                                                                                                                     
              ┃                                                             ┃                              
                                                                                                          
              ▼                                                             ▼                              
 ┌───────────────────────────────────────────────┐    ┌─────────────────────────────────────────────────────┐
 │- Respond to Command from client               │    │- Send Command to server                             │
 │- User specified port server is to run on      │    │- User specified port server is running on           │
 │- Always run on localhost where ZIO is running │    │- Client implementations planned:                    │
 │- Supported Commands Planned:                  │    │    - CLI                                            │
 │    - Fiber Dump                               │    │    - Text editor / IDE                              │
 │    - Metrics                                  │    └─────────────────────────────────────────────────────┘
 │        - prometheus                           │                                                           
 │    - Test (Just replys with a test message)   │                                                                        
 └───────────────────────────────────────────────┘                                             
</pre>
We want to give users the option to run a light weight server local to where their ZIO app is running that supports a few commands to aid monitoring and metrics of their application.

## Commands

Commands to support:

- Fiber Dump - Fiber dump of all fibers
- Metrics
    - stdout - prints metrics to stdout
    - prometheus - pushes metrics to prometheus
    - prometheus & other metrics integrations supported by utilizing `zio-metrics`
- Test 
    - Server replies with a test message - used to test server working

## Client

Clients send commands to the server and wait for a response. Initially we are planning to support a CLI tool as the client but do plan to support some level of IDE/Text editor support to aid debugging.

## Protocol

The protocol used to communicate between the client and server is a cut down version of the RESP protocol that Redis uses. For more information on this protocol please see the specification docs: [Redis Protocol specification – Redis](https://redis.io/topics/protocol)

Choosing this protocol enables us to have something simple to implement without any external dependencies being introduced and thereby keeping the client and server lightweight. 

