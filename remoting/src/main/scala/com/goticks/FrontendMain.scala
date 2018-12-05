package com.goticks
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext

object FrontendMain extends App with Startup {
  val config = ConfigFactory.load("frontend")
  implicit val system: ActorSystem = ActorSystem("frontend", config)

  val api = new RestApi {
    override def log: LoggingAdapter = Logging(system.eventStream, "frontend")

    def createPath() = {
      val config = ConfigFactory.load("frontend").getConfig("backend")
      val host = config.getString("host")
      val port = config.getInt("port")
      val protocol = config.getString("protocol")
      val systemName = config.getString("system")
      val actorName = config.getString("actor")
      s"$protocol://$systemName@$host:$port/$actorName"
    }

    override def createBoxOffice(): ActorRef = {
      val path = createPath()
      system.actorOf(Props(new RemoteLookupProxy(path)), "lookupBoxOffice")
    }
    override implicit def executionContext: ExecutionContext = system.dispatcher
    override implicit def requestTimeout: Timeout =
      configuredRequestTimeout(config)
  }

  startup(api.routes)
}
