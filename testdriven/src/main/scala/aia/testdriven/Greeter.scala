package aia.testdriven

import akka.actor.{Actor, ActorLogging}

case class Greeting(message: String)

class Greeter extends Actor with ActorLogging {
  override def receive: PartialFunction[Any, Unit] = {
    case Greeting(message) => log.info("Hello {}!", message)
  }
}
