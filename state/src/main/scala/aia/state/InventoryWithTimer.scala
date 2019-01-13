package aia.state
import akka.actor.{Actor, ActorRef, FSM}
import scala.concurrent.duration._

class InventoryWithTimer(publisher: ActorRef) extends Actor with FSM[State, StateData] {
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

  when(WaitForPublisher, stateTimeout = 5.seconds) {
    case Event(supply: BookSupply, data: StateData) => {
      goto(ProcessRequest) using data.copy(nrBooksInStore = supply.nrBooks)
    }
    case Event(BookSupplySoldOut, _) => {
      goto(ProcessSoldOut)
    }
    case Event(StateTimeout, _) => {
      goto(WaitForRequests)
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

