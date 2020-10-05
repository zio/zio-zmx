---
id: overview_diagnostics
title: "Diagnostics"
---

# Diagnostics

ZIO-ZMX Diagnostics module allows one to look inside a working application at the fiber level. It provides a simple server returning fiber dumps from the ZIO Executor.

The server is using Redis Serialization Protocol ([RESP](https://redis.io/topics/protocol)) over TCP for communication. 

## Server Configuration

To enable diagnostics in your ZIO application you will need to create a custom ZIO Runtime that is using ZIO-ZMX's fiber Supervisor, and then run your program providing the Diagnostics layer.

```scala

val exampleProgram: ZIO[Console, Throwable, Unit] =
  for {
    _ <- putStrLn("Waiting for input")
    a <- getStrLn
    _ <- putStrLn("Thank you for " + a)
  } yield ()

val zioZMXLayer = zio.zmx.Diagnostics.live("localhost", 1111)

val runtime = Runtime.default.mapPlatform(_.withSupervisor(zio.zmx.ZMXSupervisor))

runtime.unsafeRun {
  exampleProgram.provideCustomLayer(zioZMXLayer)
}

```

## Protocol

In general ZMX Diagnostics server handles commands sent as an Array of Bulk Strings. The first string is the command and the rest are the command parameters.

At the moment the server handles only to two parameterless commands:
- `dump`
- `test`

### Dump

A request for the dumps of all fibers.

Request:
```
*1\r\n$4\r\ndump\r\n
```

Response:
```
*3
+{fiber dump 1}
+{fiber dump 2}
+{fiber dump 3}
```

### Test

A test request.

Request:

```
*1\r\n$4\r\ntest\r\n
```

Response:

```
+This is a TEST
```

## Client

You can interact with ZMX Diagnostics server using any client compatible with RESP over TCP. 

For quick testing you can just use `netcat`:

```bash
echo -ne '*1\r\n$4\r\ndump\r\n' | nc localhost 1111
```

For more advanced use case you can use any RESP client library (keeping in mind the specifics of ZMX protocol described above).

ZIO-ZMX also provides a Scala client tailored to use with the Diagnostics server.

//TODO
