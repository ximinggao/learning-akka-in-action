package aia.channels
import akka.event.{ActorEventBus, EventBus, LookupClassification}

class OrderMessageBus
    extends EventBus
    with LookupClassification
    with ActorEventBus {
  override type Event = Order
  override type Classifier = Boolean
  override protected def mapSize(): Int = 2

  override protected def classify(event: OrderMessageBus#Event) = {
    event.number > 1
  }
  override protected def publish(
    event: Event,
    subscriber: Subscriber
  ): Unit = {
    subscriber ! event
  }
}
