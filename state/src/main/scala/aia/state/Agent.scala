package aia.state
import akka.actor.ActorSystem
import akka.agent.Agent

case class BookStatistics(nameBook: String, nrSold: Int)
case class StateBookStatistics(sequence: Long, books: Map[String, BookStatistics])

class BookStatisticsMgr(system: ActorSystem) {
  implicit val ex = system.dispatcher
  val stateAgent = Agent(StateBookStatistics(0, Map()))

  def addBooksSold(book: String, nrSold: Int): Unit = {

  }
}
