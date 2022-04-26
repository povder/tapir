package sttp.tapir.server.play

import akka.actor.ActorSystem
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.matchers.should.Matchers._
import play.api.Mode
import play.api.routing.Router
import play.core.server.{DefaultAkkaHttpServerComponents, ServerConfig}
import sttp.client3._
import sttp.model.{Part, StatusCode}
import sttp.tapir._
import sttp.tapir.tests.Test

import scala.concurrent.Future

class PlayServerWithContextTest(backend: SttpBackend[IO, Any])(implicit _actorSystem: ActorSystem) {
  import _actorSystem.dispatcher

  def tests(): List[Test] = List(
    Test("server with play.http.context set") {
      val e = endpoint.get.in("hello").out(stringBody).serverLogicSuccess(_ => Future.successful("world"))
      val components = new DefaultAkkaHttpServerComponents {
        override lazy val serverConfig: ServerConfig = ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test)
        override lazy val actorSystem: ActorSystem = ActorSystem("tapir", defaultExecutionContext = Some(_actorSystem.dispatcher))
        override def router: Router = Router.from(PlayServerInterpreter().toRoutes(e)).withPrefix("/test")
      }
      val s = components.server
      val r = Future.successful(()).flatMap { _ =>
        basicRequest
          .get(uri"http://localhost:${s.mainAddress.getPort}/test/hello")
          .send(backend)
          .map(_.body shouldBe Right("world"))
          .unsafeToFuture()
      }
      r.onComplete(_ => s.stop())
      r
    },
    Test("reject big body in multipart request") {
      import sttp.tapir.generic.auto._
      case class A(part1: Part[String])
      val e = endpoint.post.in("hello").in(multipartBody[A]).out(stringBody).serverLogicSuccess(_ => Future.successful("world"))
      val components = new DefaultAkkaHttpServerComponents {
        override lazy val serverConfig: ServerConfig = ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test)
        override lazy val actorSystem: ActorSystem = ActorSystem("tapir", defaultExecutionContext = Some(_actorSystem.dispatcher))
        override def router: Router = Router.from(PlayServerInterpreter().toRoutes(e)).withPrefix("/test")
      }
      val s = components.server
      val r = Future.successful(()).flatMap { _ =>
        basicRequest
          .post(uri"http://localhost:${s.mainAddress.getPort}/test/hello")
          .body(Array.ofDim[Byte](1024 * 15000)) // 15M
          .send(backend)
          .map(_.code shouldBe StatusCode.PayloadTooLarge)
          .unsafeToFuture()
      }
      r.onComplete(_ => s.stop())
      r
    },
    Test("reject big body in normal request") {
      import sttp.tapir.generic.auto._
      case class A(part1: Part[String])
      val e = endpoint.post.in("hello").in(stringBody).out(stringBody).serverLogicSuccess(_ => Future.successful("world"))
      val components = new DefaultAkkaHttpServerComponents {
        override lazy val serverConfig: ServerConfig = ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Test)
        override lazy val actorSystem: ActorSystem = ActorSystem("tapir", defaultExecutionContext = Some(_actorSystem.dispatcher))
        override def router: Router = Router.from(PlayServerInterpreter().toRoutes(e)).withPrefix("/test")
      }
      val s = components.server
      val r = Future.successful(()).flatMap { _ =>
        basicRequest
          .post(uri"http://localhost:${s.mainAddress.getPort}/test/hello")
          .body(Array.ofDim[Byte](1024 * 15000)) // 15M
          .send(backend)
          .map(_.code shouldBe StatusCode.PayloadTooLarge)
          .unsafeToFuture()
      }
      r.onComplete(_ => s.stop())
      r
    }
  )
}
