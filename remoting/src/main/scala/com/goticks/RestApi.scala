package com.goticks
import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import scala.concurrent.ExecutionContext

trait RestApi extends BoxOfficeApi with EventMarshalling {
  import StatusCodes._

  def routes: Route = eventsRoute ~ eventRoute ~ ticketsRoute

  def eventsRoute: Route =
    pathPrefix("events") {
      pathEndOrSingleSlash {
        get {
          onSuccess(getEvents()) { events =>
            complete(OK, events)
          }
        }
      }
    }

  def eventRoute: Route =
    pathPrefix("events" / Segment) { event =>
      pathEndOrSingleSlash {
        post {
          entity(as[EventDescription]) { ed =>
            onSuccess(createEvent(event, ed.tickets)) {
              case BoxOffice.EventCreated(event) => complete(Created, event)
              case BoxOffice.EventExists =>
                val err = Error(s"$event event exists already.")
                complete(BadRequest, err)
            }
          }
        } ~
          get {
            onSuccess(getEvent(event)) {
              _.fold(complete(NotFound))(e => complete(OK, e))
            }
          } ~
          delete {
            onSuccess(cancelEvent(event)) {
              _.fold(complete(NotFound))(e => complete(OK, e))
            }
          }
      }
    }

  def ticketsRoute: Route =
    pathPrefix("events" / Segment / "tickets") { event =>
      post {
        pathEndOrSingleSlash {
          entity(as[TicketRequest]) { request =>
            onSuccess(requestTickets(event, request.tickets)) { tickets =>
              if (tickets.entries.isEmpty) complete(NotFound)
              else complete(Created, tickets)
            }
          }
        }
      }
    }
}

trait BoxOfficeApi {
  import BoxOffice._
  import akka.pattern.ask

  def log: LoggingAdapter
  def createBoxOffice(): ActorRef

  implicit def executionContext: ExecutionContext
  implicit def requestTimeout: Timeout

  val boxOffice = createBoxOffice()

  def createEvent(event: String, nrOfTickets: Int) = {
    log.info(s"Received new event: $event, sending to $boxOffice")
    boxOffice.ask(CreateEvent(event, nrOfTickets)).mapTo[EventResponse]
  }

  def getEvents() = boxOffice.ask(GetEvents).mapTo[Events]

  def getEvent(event: String) =
    boxOffice.ask(GetEvent(event)).mapTo[Option[Event]]

  def cancelEvent(event: String) =
    boxOffice.ask(CancelEvent(event)).mapTo[Option[Event]]

  def requestTickets(event: String, tickets: Int) =
    boxOffice.ask(GetTickets(event, tickets)).mapTo[TicketSeller.Tickets]
}
