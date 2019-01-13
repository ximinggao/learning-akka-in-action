package aia.state
import akka.actor.Actor

import scala.math.min

class Publisher(totalNrBooks: Int, nrBooksPerRequest: Int) extends Actor {
  var nrLeft = totalNrBooks
  override def receive: Receive = {
    case PublisherRequest => {
      if (nrLeft == 0)
        sender() ! BookSupplySoldOut
      else {
        val supply = min(nrBooksPerRequest, nrLeft)
        nrLeft -= supply
        sender() ! BookSupply(supply)
      }
    }
  }
}
