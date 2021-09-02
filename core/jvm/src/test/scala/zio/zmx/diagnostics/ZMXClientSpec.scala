/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.zmx.diagnostics

import java.nio.charset.StandardCharsets.UTF_8

import zio.test.Assertion._
import zio.test._
import zio._
import java.nio.channels.ServerSocketChannel
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer

object ZMXClientSpec extends DefaultRunnableSpec {

  val zmxClient = new ZMXClient(ZMXConfig.default)

  /** Helper for creating `Chunk[Byte]` from `String` */
  private def bytes(value: String): Chunk[Byte] =
    Chunk.fromArray(value.getBytes(UTF_8))

  def spec =
    suite("ZMXClientSpec")(
      suite("Using the ZMXClient")(
        test("zmx test generating a successful command") {
          assert {
            ZMXClient.generateRespCommand(args = Chunk("foobar"))
          } {
            equalTo(bytes("*1\r\n$6\r\nfoobar\r\n"))
          }
        },
        test("zmx test generating a successful multiple command") {
          assert {
            ZMXClient.generateRespCommand(args = Chunk("foo", "bar"))
          } {
            equalTo(bytes("*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"))
          }
        },
        test("zmx test generating a successful empty command") {
          assert {
            ZMXClient.generateRespCommand(args = Chunk.empty)
          } {
            equalTo(bytes("*0\r\n"))
          }
        },
        testM("zmx client test sending commands") {
          openSocket.use { server =>
            for {
              clientFiber <- zmxClient.sendCommand(Chunk("foo")).fork
              clientOpt   <- ZIO.effect(server.accept).option
              client      <- ZIO.fromOption(clientOpt)

              buf <- UIO.effectTotal(ByteBuffer.allocate(256))
              _   <- IO.effect(client.read(buf)).refineToOrDie[IOException]
              _   <- UIO.effectTotal(buf.flip)
              req <- ZIO.succeed(UTF_8.decode(buf).toString)

              _    <- IO.effect(client.write(ByteBuffer.wrap(bytes("*0\r\n").toArray))).refineToOrDie[IOException]
              _    <- IO.effect(client.close()).refineToOrDie[IOException]
              exit <- clientFiber.await
              resp  = exit.getOrElse(_ => "NOPE")
            } yield assert((req, resp))(equalTo(("*1\r\n$3\r\nfoo\r\n", "*0\r\n")))
          }
        }
      )
    )

  private val openSocket = {
    val acq = for {
      channel <- IO.effect(ServerSocketChannel.open()).refineToOrDie[IOException]
      addr    <- UIO.effectTotal(new InetSocketAddress("localhost", 1111))
      _       <- IO.effect(channel.bind(addr)).refineToOrDie[IOException].unit
    } yield channel

    ZManaged.make(acq)(channel => ZIO.effect(channel.close()).orDie)
  }
}
