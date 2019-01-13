package aia.channels
import java.util.Date

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.duration._

class CancelOrder(time: Date,
                  override val customerId: String,
                  override val productId: String,
                  override val number: Int)
    extends Order(customerId, productId, number)

class EventStreamTest
    extends TestKit(ActorSystem("EventStreamTest"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MustMatchers {
  override protected def afterAll(): Unit = {
    system.terminate()
  }

  "The EventStream" must {
    "distribute messages" in {
      val deliverOrder = TestProbe()
      val giftModule = TestProbe()

      system.eventStream.subscribe(deliverOrder.ref, classOf[Order])
      system.eventStream.subscribe(giftModule.ref, classOf[Order])

      val msg =
        Order(customerId = "me", productId = "Akka in Action", number = 2)
      system.eventStream.publish(msg)

      deliverOrder.expectMsg(msg)
      giftModule.expectMsg(msg)
    }
    "monitor hierachy" in {
      val giftModule = TestProbe()

      system.eventStream.subscribe(giftModule.ref, classOf[Order])

      val msg = new Order("me", "Akka in Action", 3)
      system.eventStream.publish(msg)

      giftModule.expectMsg(msg)

      val msg2 = new CancelOrder(new Date(), "me", "Akka in Action", 2)
      system.eventStream.publish(msg2)

      giftModule.expectMsg(msg2)
    }
    "Ignore other messages" in {
      val giftModule = TestProbe()

      system.eventStream.subscribe(giftModule.ref, classOf[CancelOrder])
      val msg = new Order("me", "Akka in Action", 3)
      system.eventStream.publish(msg)
      giftModule.expectNoMessage(3.seconds)
    }
    "unscribe messages" in {

      val DeliverOrder = TestProbe()
      val giftModule = TestProbe()

      system.eventStream.subscribe(DeliverOrder.ref, classOf[Order])
      system.eventStream.subscribe(giftModule.ref, classOf[Order])

      val msg = new Order("me", "Akka in Action", 3)
      system.eventStream.publish(msg)

      DeliverOrder.expectMsg(msg)
      giftModule.expectMsg(msg)

      system.eventStream.unsubscribe(giftModule.ref)

      system.eventStream.publish(msg)
      DeliverOrder.expectMsg(msg)
      giftModule.expectNoMessage(3 seconds)
    }
  }

  "The OrderMessageBus" must {
    "deliver Order messages" in {
      val bus = new OrderMessageBus

      val singleBooks = TestProbe()
      bus.subscribe(singleBooks.ref, false)
      val multiBooks = TestProbe()
      bus.subscribe(multiBooks.ref, true)

      val msg = Order("me", "Akka in Action", 1)
      bus.publish(msg)
      singleBooks.expectMsg(msg)
      multiBooks.expectNoMessage(3 seconds)

      val msg2 = Order("me", "Akka in Action", 3)
      bus.publish(msg2)
      singleBooks.expectNoMessage(3 seconds)
      multiBooks.expectMsg(msg2)
    }
  }
}
