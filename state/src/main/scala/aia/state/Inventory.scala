package aia.state
import akka.actor.{Actor, ActorRef, FSM}

case class BookRequest(context: AnyRef, target: ActorRef)
case class BookSupply(nrBooks: Int)
case object BookSupplySoldOut
case object Done
case object PendingRequests

case object PublisherRequest
case class BookReply(context: AnyRef, reserveId: Either[String, Int])

sealed trait State
case object WaitForRequests extends State
case object ProcessRequest extends State
case object WaitForPublisher extends State
case object SoldOut extends State
case object ProcessSoldOut extends State

case class StateData(nrBooksInStore: Int, pendingRequests: Seq[BookRequest])

class Inventory(publisher: ActorRef) extends Actor with FSM[State, StateData] {
  var reserveId = 0
  startWith(WaitForRequests, StateData(0, Seq()))

  when(WaitForRequests) {
    case Event(request: BookRequest, stateData) => {
      val newStateData =
        stateData.copy(pendingRequests = stateData.pendingRequests :+ request)
      if (newStateData.nrBooksInStore > 0) {
        goto(ProcessRequest) using newStateData
      } else {
        goto(WaitForPublisher) using newStateData
      }
    }
    case Event(PendingRequests, data) => {
      if (data.pendingRequests.isEmpty) {
        stay
      } else if (data.nrBooksInStore > 0) {
        goto(ProcessRequest)
      } else {
        goto(WaitForPublisher)
      }
    }
  }

  when(WaitForPublisher) {
    case Event(supply: BookSupply, data: StateData) => {
      goto(ProcessRequest) using data.copy(nrBooksInStore = supply.nrBooks)
    }
    case Event(BookSupplySoldOut, _) => {
      goto(ProcessSoldOut)
    }
  }

  when(ProcessRequest) {
    case Event(Done, data: StateData) => {
      goto(WaitForRequests) using data.copy(
        nrBooksInStore = data.nrBooksInStore - 1,
        pendingRequests = data.pendingRequests.tail
      )
    }
  }

  when(SoldOut) {
    case Event(request: BookRequest, data: StateData) => {
      goto(ProcessSoldOut) using StateData(0, Seq(request))
    }
  }

  when(ProcessSoldOut) {
    case Event(Done, data: StateData) => {
      goto(SoldOut) using StateData(0, Seq())
    }
  }

  whenUnhandled {
    case Event(request: BookRequest, data: StateData) => {
      stay using data.copy(pendingRequests = data.pendingRequests :+ request)
    }
    case Event(e, s) => {
      log.warning(
        "received unhandled request {} in state {}/{}",
        e,
        stateName,
        s
      )
      stay
    }
  }

  onTransition {
    case _ -> WaitForRequests => {
      if (nextStateData.pendingRequests.nonEmpty) {
        self ! PendingRequests
      }
    }
    case _ -> WaitForPublisher => {
      publisher ! PublisherRequest
    }
    case _ -> ProcessRequest => {
      val request = nextStateData.pendingRequests.head
      reserveId += 1
      request.target ! BookReply(request.context, Right(reserveId))
      self ! Done
    }
    case _ -> ProcessSoldOut => {
      nextStateData.pendingRequests.foreach(request => {
        request.target ! BookReply(request.context, Left("SoldOut"))
      })
      self ! Done
    }
  }

  initialize
}
