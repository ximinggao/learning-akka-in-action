package aia.faulttolerance

import aia.faulttolerance.LifeCycleHooks.{ForceRestart, ForceRestartException}
import akka.actor.{Actor, ActorLogging}

class LifeCycleHooks extends Actor with ActorLogging {
  log.info("Constructor")


  override def preStart(): Unit = {
    log.info("PreStart")
  }

  override def postStop(): Unit = {
    log.info("postStop")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    log.info(s"preRestart. Reason: $reason when handling message: $message")
    super.preRestart(reason, message)
  }

  override def postRestart(reason: Throwable): Unit = {
    log.info(s"postRestart. Reason: $reason")
    super.postRestart(reason)
  }

  override def receive: Receive = {
    case ForceRestart =>
      throw new ForceRestartException
    case msg =>
      log.info(s"Received: '$msg'. Sending back")
      sender() ! msg
  }
}

object LifeCycleHooks {

  object SampleMessage

  object ForceRestart

  private class ForceRestartException extends IllegalArgumentException("force restart")

}
