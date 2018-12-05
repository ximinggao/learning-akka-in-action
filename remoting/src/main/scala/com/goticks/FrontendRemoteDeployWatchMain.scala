package com.goticks
import akka.actor.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext

object FrontendRemoteDeployWatchMain extends App with Startup {
  val config = ConfigFactory.load("frontend-remote-deploy")
  implicit val system = ActorSystem("frontend", config)

  val api = new RestApi {
    override def log: LoggingAdapter =
      Logging(system.eventStream, "frontend-remote-watch")
    override def createBoxOffice(): ActorRef = {
      system.actorOf(
        RemoteBoxOfficeForwarder.props,
        RemoteBoxOfficeForwarder.name
      )
    }
    override implicit def executionContext: ExecutionContext = system.dispatcher
    override implicit def requestTimeout: Timeout =
      configuredRequestTimeout(config)
  }

  startup(api.routes)
}
