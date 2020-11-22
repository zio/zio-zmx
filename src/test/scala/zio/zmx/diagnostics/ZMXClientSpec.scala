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

import java.nio.charset.StandardCharsets

import zio.nio.core.channels.ServerSocketChannel
import zio.nio.core.{ Buffer, SocketAddress }
import zio.test.Assertion._
import zio.test._
import zio.{ Chunk, ZIO }

object ZMXClientSpec extends DefaultRunnableSpec {

  val zmxClient = new ZMXClient(ZMXConfig.empty)

  def spec =
    suite("ZMXClientSpec")(
      suite("Using the ZMXClient")(
        test("zmx test generating a successful command") {
          val p: String = ZMXClient.generateRespCommand(args = List("foobar"))
          assert(p)(equalTo("*1\r\n$6\r\nfoobar\r\n"))
        },
        test("zmx test generating a successful multiple command") {
          val p: String = ZMXClient.generateRespCommand(args = List("foo", "bar"))
          assert(p)(equalTo("*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n"))
        },
        test("zmx test generating a successful empty command") {
          val p: String = ZMXClient.generateRespCommand(args = List())
          assert(p)(equalTo("*0\r\n"))
        },
        testM("zmx client test sending commands") {
          for {
            server <- ServerSocketChannel.open
            addr   <- SocketAddress.inetSocketAddress("localhost", 1111)
            _      <- server.bind(addr)

            clientFiber <- zmxClient.sendCommand(List("foo")).fork
            clientOpt   <- server.accept
            client      <- ZIO.fromOption(clientOpt)

            buf  <- Buffer.byte(256)
            _    <- client.read(buf)
            _    <- buf.flip
            req  <- buf.withJavaBuffer(b => ZIO.succeed(StandardCharsets.UTF_8.decode(b).toString))
            msg  <- Buffer.byte(Chunk("*0\r\n".getBytes().toSeq: _*))
            _    <- client.write(msg)
            _    <- client.close
            exit <- clientFiber.await
            resp  = exit.getOrElse(_ => "NOPE")
            _    <- server.close
          } yield assert((req, resp))(equalTo(("*1\r\n$3\r\nfoo\r\n", "*0\r\n")))
        }
      )
    )
}
