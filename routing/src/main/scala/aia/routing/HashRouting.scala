package aia.routing
import akka.actor.{Actor, ActorRef}
import akka.routing.ConsistentHashingRouter.ConsistentHashable

trait GatherMessage {
  val id: String
  val values: Seq[String]
}

case class GatherMessageNormalImpl(id: String, values: Seq[String])
    extends GatherMessage

case class GatherMessageWithHash(id: String, values: Seq[String])
    extends GatherMessage
    with ConsistentHashable {
  override def consistentHashKey: Any = id
}

class SimpleGather(nextStep: ActorRef) extends Actor {
  var messages = Map[String, GatherMessage]()

  override def receive: Receive = {
    case msg: GatherMessage => {
      messages.get(msg.id) match {
        case Some(previous) => {
          nextStep ! GatherMessageNormalImpl(
            msg.id,
            previous.values ++ msg.values
          )
          println(s"Joined: ${msg.id} by ${self.path.name}")
          messages -= msg.id
        }
        case None => messages += msg.id -> msg
      }
    }
  }
}
