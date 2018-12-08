package aia.structure
import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.{Actor, ActorRef}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration

case class PhotoMessage(id: String,
                        photo: String,
                        creationTime: Option[Date] = None,
                        speed: Option[Int] = None)

object ImageProcessing {
  val dateFormat = new SimpleDateFormat("ddMMyyyy HH:mm:ss.SSS")

  def getSpeed(image: String): Option[Int] = {
    val attributes = image.split('|')
    if (attributes.size == 3)
      Some(attributes(1).toInt)
    else
      None
  }

  def getTime(image: String): Option[Date] = {
    val attributes = image.split('|')
    if (attributes.size == 3)
      Some(dateFormat.parse(attributes(0)))
    else
      None
  }

  def getLicense(image: String): Option[String] = {
    val attributes = image.split('|')
    if (attributes.size == 3)
      Some(attributes(2))
    else
      None
  }

  def createPhotoString(date: Date, speed: Int, license: String): String = {
    "%s|%s|%s".format(dateFormat.format(date), speed, license)
  }

  def createPhotoString(date: Date, speed: Int): String = {
    createPhotoString(date, speed, " ")
  }
}

class GetSpeed(pipe: ActorRef) extends Actor {
  override def receive: Receive = {
    case msg: PhotoMessage => {
      pipe ! msg.copy(speed = ImageProcessing.getSpeed(msg.photo))
    }
  }
}

class GetTime(pipe: ActorRef) extends Actor {
  override def receive: Receive = {
    case msg: PhotoMessage => {
      pipe ! msg.copy(creationTime = ImageProcessing.getTime(msg.photo))
    }
  }
}

class RecipientList(recipients: Seq[ActorRef]) extends Actor {
  override def receive: Receive = {
    case msg: AnyRef => recipients.foreach(_ ! msg)
  }
}

case class TimeoutMessage(msg: PhotoMessage)

class Aggregator(timeout: FiniteDuration, pipe: ActorRef) extends Actor {
  val messages = new ListBuffer[PhotoMessage]
  implicit val ec = context.system.dispatcher

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    messages.foreach(self ! _)
    messages.clear()
  }

  override def receive: Receive = {
    case rcvMsg: PhotoMessage => {
      messages.find(_.id == rcvMsg.id) match {
        case Some(alreadyRcvMsg) => {
          val newCombinedMsg = PhotoMessage(
            rcvMsg.id,
            rcvMsg.photo,
            rcvMsg.creationTime.orElse(alreadyRcvMsg.creationTime),
            rcvMsg.speed.orElse(alreadyRcvMsg.speed))
          pipe ! newCombinedMsg
          messages -= alreadyRcvMsg
        }
        case None => {
          messages += rcvMsg
          context.system.scheduler.scheduleOnce(timeout, self, TimeoutMessage(rcvMsg))
        }
      }
    }

    case TimeoutMessage(rcvMsg) => {
      messages.find(_.id == rcvMsg.id) match {
        case Some(alreadyRcvMsg) => {
          pipe ! alreadyRcvMsg
          messages -= alreadyRcvMsg
        }
        case None => // message is already processed
      }
    }

    case ex: Exception => throw ex
  }
}
