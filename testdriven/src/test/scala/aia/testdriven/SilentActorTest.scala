package aia.testdriven

import akka.actor.{Actor, ActorSystem}
import akka.testkit.TestKit
import org.scalatest.{MustMatchers, WordSpecLike}

package singlethread {

  import akka.actor.ActorRef
  import akka.testkit.TestActorRef

  class SilentActorTest extends TestKit(ActorSystem("test-system"))
    with WordSpecLike
    with MustMatchers
    with StopSystemAfterAll {
    "A Silent Actor" must {
      "change state when it receives a message, single threaded" in {
        import SilentActor._

        val silentActor = TestActorRef[SilentActor]
        silentActor ! SilentMessage("whisper")
        silentActor.underlyingActor.state must contain("whisper")
      }
    }
  }

  class SilentActor extends Actor {

    import SilentActor._

    var internalState = Vector[String]()

    override def receive: Receive = {
      case SilentMessage(data) =>
        internalState = internalState :+ data
    }

    def state = internalState
  }

  object SilentActor {

    case class SilentMessage(data: String)

    case class GetState(receiver: ActorRef)

  }

}

package multithread {

  import akka.actor.{ActorRef, Props}

  class SilentActorTest extends TestKit(ActorSystem("test-system"))
    with WordSpecLike
    with MustMatchers
    with StopSystemAfterAll {
    "A Silent Actor" must {
      "change internal state when it receives a message, multi-threaded" in {
        import SilentActor._

        val silentActor = system.actorOf(Props[SilentActor], "sa")
        silentActor ! SilentMessage("whisper1")
        silentActor ! SilentMessage("whisper2")
        silentActor ! GetState(testActor)
        expectMsg(Vector("whisper1", "whisper2"))
      }
    }
  }

  class SilentActor extends Actor {

    import SilentActor._

    var internalState = Vector[String]()

    override def receive = {
      case SilentMessage(data) =>
        internalState = internalState :+ data
      case GetState(receiver) =>
        receiver ! internalState
    }
  }

  object SilentActor {

    case class SilentMessage(data: String)

    case class GetState(receiver: ActorRef)

  }

}
