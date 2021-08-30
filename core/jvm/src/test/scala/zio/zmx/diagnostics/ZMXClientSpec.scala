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

import zio.nio.core.channels.ServerSocketChannel
import zio.nio.core.{ Buffer, SocketAddress }
import zio.test.Assertion._
import zio.test._
import zio.{ Chunk, ZIO, ZManaged }

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
              clientOpt   <- server.accept
              client      <- ZIO.fromOption(clientOpt)

              buf <- Buffer.byte(256)
              _   <- client.read(buf)
              _   <- buf.flip
              req <- buf.withJavaBuffer(b => ZIO.succeed(UTF_8.decode(b).toString))

              _    <- client.write(bytes("*0\r\n"))
              _    <- client.close
              exit <- clientFiber.await
              resp  = exit.getOrElse(_ => "NOPE")
            } yield assert((req, resp))(equalTo(("*1\r\n$3\r\nfoo\r\n", "*0\r\n")))
          }
        }
      )
    )

  private val openSocket = {
    val acq = for {
      server <- ServerSocketChannel.open
      addr   <- SocketAddress.inetSocketAddress("localhost", 1111)
      _      <- server.bind(addr)
    } yield server

    ZManaged.make(acq)(_.close.orDie)
  }
}
