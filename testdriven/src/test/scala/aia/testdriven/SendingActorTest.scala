package aia.testdriven

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.TestKit
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.util.Random

class SendingActorTest extends TestKit(ActorSystem("testsystem"))
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {
  "A Sending Actor" must {
    "send a message to another actor when it has finished processing" in {
      import SendingActor._

      val sendingActor = system.actorOf(props(testActor), "SendingActor")

      val size = 1000
      val maxInclusive = 100000

      def randomEvents(): Vector[Event] =
        (0 until size).map {
          _ => Event(Random.nextInt(maxInclusive))
        }.toVector

      val unsorted = randomEvents()
      sendingActor ! SortEvents(unsorted)

      expectMsgPF() {
        case SortedEvents(events) =>
          events.size must be(size)
          unsorted.sortBy(_.id) must be(events)
      }
    }
  }
}

object SendingActor {
  def props(receiver: ActorRef) = Props(new SendingActor(receiver))

  case class Event(id: Long)

  case class SortEvents(unsorted: Vector[Event])

  case class SortedEvents(sorted: Vector[Event])

}

class SendingActor(receiver: ActorRef) extends Actor {

  import SendingActor._

  override def receive: Receive = {
    case SortEvents(unsorted) =>
      receiver ! SortedEvents(unsorted.sortBy(_.id))
  }
}
