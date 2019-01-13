package aia.state
import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import scala.concurrent.duration._

class InventoryTest
    extends TestKit(ActorSystem("InventoryTest"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MustMatchers
    with ImplicitSender {
  override protected def afterAll(): Unit = system.terminate()

  "Inventory" must {
    "follow the flow" in {
      val publisher = system.actorOf(Props(new Publisher(2, 2)))
      val inventory = system.actorOf(Props(new Inventory(publisher)))
      val stateProbe = TestProbe()
      val replyProbe = TestProbe()

      inventory ! SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(CurrentState(inventory, WaitForRequests))

      inventory ! BookRequest("context1", replyProbe.ref)
      stateProbe.expectMsg(
        Transition(inventory, WaitForRequests, WaitForPublisher)
      )
      stateProbe.expectMsg(
        Transition(inventory, WaitForPublisher, ProcessRequest)
      )
      stateProbe.expectMsg(
        Transition(inventory, ProcessRequest, WaitForRequests)
      )
      replyProbe.expectMsg(BookReply("context1", Right(1)))

      inventory ! BookRequest("context2", replyProbe.ref)
      stateProbe.expectMsg(
        Transition(inventory, WaitForRequests, ProcessRequest)
      )
      stateProbe.expectMsg(
        Transition(inventory, ProcessRequest, WaitForRequests)
      )
      replyProbe.expectMsg(BookReply("context2", Right(2)))

      inventory ! BookRequest("context3", replyProbe.ref)
      stateProbe.expectMsg(
        Transition(inventory, WaitForRequests, WaitForPublisher)
      )
      stateProbe.expectMsg(
        Transition(inventory, WaitForPublisher, ProcessSoldOut)
      )
      replyProbe.expectMsg(BookReply("context3", Left("SoldOut")))
      stateProbe.expectMsg(Transition(inventory, ProcessSoldOut, SoldOut))

      inventory ! BookRequest("context4", replyProbe.ref)
      stateProbe.expectMsg(Transition(inventory, SoldOut, ProcessSoldOut))
      replyProbe.expectMsg(BookReply("context4", Left("SoldOut")))
      stateProbe.expectMsg(Transition(inventory, ProcessSoldOut, SoldOut))
      system.stop(inventory)
      system.stop(publisher)
    }

    "process multiple requests" in {
      val publisher = system.actorOf(Props(new Publisher(2, 2)))
      val inventory = system.actorOf(Props(new Inventory(publisher)))
      val stateProbe = TestProbe()
      val replyProbe = TestProbe()

      inventory ! SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(CurrentState(inventory, WaitForRequests))

      inventory ! BookRequest("context1", replyProbe.ref)
      inventory ! BookRequest("context2", replyProbe.ref)
      stateProbe.expectMsg(Transition(inventory, WaitForRequests, WaitForPublisher))
      stateProbe.expectMsg(Transition(inventory, WaitForPublisher, ProcessRequest))
      stateProbe.expectMsg(Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(BookReply("context1", Right(1)))
      stateProbe.expectMsg(Transition(inventory, WaitForRequests, ProcessRequest))
      stateProbe.expectMsg(Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(BookReply("context2", Right(2)))

      system.stop(inventory)
      system.stop(publisher)
    }

    "support multiple supplies" in {
      val publisher = system.actorOf(Props(new Publisher(4, 2)))
      val inventory = system.actorOf(Props(new Inventory(publisher)))
      val stateProbe = TestProbe()
      val replyProbe = TestProbe()

      inventory ! SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(
        CurrentState(inventory, WaitForRequests))

      //start test
      inventory ! BookRequest("context1", replyProbe.ref)
      stateProbe.expectMsg(
        Transition(inventory, WaitForRequests, WaitForPublisher))
      stateProbe.expectMsg(
        Transition(inventory, WaitForPublisher, ProcessRequest))
      stateProbe.expectMsg(
        Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(BookReply("context1", Right(1)))

      inventory ! BookRequest("context2", replyProbe.ref)
      stateProbe.expectMsg(
        Transition(inventory, WaitForRequests, ProcessRequest))
      stateProbe.expectMsg(
        Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(BookReply("context2", Right(2)))

      inventory ! BookRequest("context3", replyProbe.ref)
      stateProbe.expectMsg(
        Transition(inventory, WaitForRequests, WaitForPublisher))
      stateProbe.expectMsg(
        Transition(inventory, WaitForPublisher, ProcessRequest))
      stateProbe.expectMsg(
        Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(BookReply("context3", Right(3)))

      inventory ! BookRequest("context4", replyProbe.ref)
      stateProbe.expectMsg(
        Transition(inventory, WaitForRequests, ProcessRequest))
      stateProbe.expectMsg(
        Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(BookReply("context4", Right(4)))

      system.stop(inventory)
      system.stop(publisher)
    }
  }

  "InventoryWithTimer" must {
    "follow the flow" in {
      val publisher = TestProbe()
      val inventory = system.actorOf(Props(new InventoryWithTimer(publisher.ref)))
      val stateProbe = TestProbe()
      val replyProbe = TestProbe()

      inventory ! SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(CurrentState(inventory, WaitForRequests))

      inventory ! BookRequest("context1", replyProbe.ref)
      stateProbe.expectMsg(Transition(inventory, WaitForRequests, WaitForPublisher))
      publisher.expectMsg(PublisherRequest)
      stateProbe.expectMsg(6 seconds,
        Transition(inventory, WaitForPublisher, WaitForRequests))
      stateProbe.expectMsg(Transition(inventory, WaitForRequests, WaitForPublisher))

      system.stop(inventory)
    }
  }
}
