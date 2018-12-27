package aia.routing
import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}
import akka.dispatch.Dispatchers
import akka.routing._

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

case class PerformanceRoutingMessage(photo: String, license: Option[String], processedBy: Option[String])

case class SetService(id: String, serviceTime: FiniteDuration)

class GetLicense(pipe: ActorRef, initialServiceTime: FiniteDuration = 0 millis) extends Actor {
  var id = self.path.name
  var serviceTime = initialServiceTime

  override def receive: Receive = {
    case init: SetService => {
      id = init.id
      serviceTime = init.serviceTime
      Thread.sleep(100)
    }
    case msg: PerformanceRoutingMessage => {
      Thread.sleep(serviceTime.toMillis)
      pipe ! msg.copy(
        license = ImageProcessing.getLicense(msg.photo),
        processedBy = Some(id)
      )
    }
  }
}

class RedirectActor(pipe: ActorRef) extends Actor {
  println("RedirectActor instance created")
  override def receive: Receive = {
    case msg: AnyRef => {
      pipe ! msg
    }
  }
}

class SpeedRouteLogic(minSpeed: Int,
                      normalFlowPath: String,
                      cleanupPath: String)
    extends RoutingLogic {
  override def select(message: Any,
                      routees: immutable.IndexedSeq[Routee]): Routee = {
    message match {
      case msg: Photo =>
        if (msg.speed > minSpeed)
          findRoutee(routees, normalFlowPath)
        else
          findRoutee(routees, cleanupPath)
    }
  }

  def findRoutee(routees: immutable.IndexedSeq[Routee],
                 path: String): Routee = {
    val routeeList = routees.flatMap {
      case routee: ActorRefRoutee    => immutable.IndexedSeq(routee)
      case SeveralRoutees(routeeSeq) => routeeSeq
    }

    val search = routeeList.find {
      case routee: ActorRefRoutee => routee.ref.path.toString.endsWith(path)
    }
    search.getOrElse(NoRoutee)
  }
}

case class SpeedRouterPool(minSpeed: Int, normalFlow: Props, cleanup: Props)
    extends Pool {
  override def nrOfInstances(sys: ActorSystem): Int = 1
  override def resizer: Option[Resizer] = None
  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy()(SupervisorStrategy.defaultDecider)

  override def createRouter(system: ActorSystem): Router = {
    new Router(new SpeedRouteLogic(minSpeed, "normalFlow", "cleanup"))
  }

  override val routerDispatcher: String = Dispatchers.DefaultDispatcherId

  override def newRoutee(routeeProps: Props, context: ActorContext): Routee = {
    println("Calling my new Routee() method")

    val normal = context.actorOf(normalFlow, "normalFlow")
    val clean = context.actorOf(cleanup, "cleanup")

    SeveralRoutees(
      immutable
        .IndexedSeq[Routee](ActorRefRoutee(normal), ActorRefRoutee(clean))
    )
  }
}
