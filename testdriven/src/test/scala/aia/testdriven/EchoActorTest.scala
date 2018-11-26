package aia.testdriven

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.WordSpecLike

import scala.concurrent.Await
import scala.util.{Failure, Success}

class EchoActorTest extends TestKit(ActorSystem("testsystem"))
  with WordSpecLike
  with ImplicitSender
  with StopSystemAfterAll {
  "An EchoActor" must {
    "Reply with the same message it receives" in {
      import scala.concurrent.duration._
      import akka.pattern.ask

      implicit val timeout = Timeout(3 seconds)
      implicit val ec = system.dispatcher
      val echo = system.actorOf(Props[EchoActor], "echo1")
      val future = echo ? "some message"
      future.onComplete {
        case Failure(_) =>
        case Success(msg) =>
      }

      Await.ready(future, timeout.duration)
    }

    "Reply with the same message it receives without ask" in {
      val echo = system.actorOf(Props[EchoActor], "echo2")
      echo ! "some message"
      expectMsg("some message")
    }
  }
}

class EchoActor extends Actor {
  override def receive: Receive = {
    case msg => sender() ! msg
  }
}
