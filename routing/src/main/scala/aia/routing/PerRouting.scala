package aia.routing
import akka.actor.{Actor, ActorRef, Kill, PoisonPill, Props, Terminated}
import akka.routing._

class DynamicRouteeSizer(nrActors: Int, props: Props, router: ActorRef)
    extends Actor {
  var nrChildren = nrActors
  var childInstanceNr = 0

  override def preStart(): Unit = {
    super.preStart()
    (0 until nrChildren).foreach(nr => createRoutee())
  }

  def createRoutee(): Unit = {
    childInstanceNr += 1
    val child = context.actorOf(props, "routee" + childInstanceNr)
    val selection = context.actorSelection(child.path)
    router ! AddRoutee(ActorSelectionRoutee(selection))
    context.watch(child)
  }

  override def receive: Receive = {
    case PreferredSize(size) => {
      if (size < nrChildren) {
        context.children
          .take(nrChildren - size)
          .foreach(ref => {
            val selection = context.actorSelection(ref.path)
            router ! RemoveRoutee(ActorSelectionRoutee(selection))
          })
        router ! GetRoutees
      } else {
        (nrChildren until size).foreach(nr => createRoutee())
      }
      nrChildren = size
    }

    case routees: Routees => {
      import collection.JavaConverters._
      val active = routees.getRoutees.asScala.map {
        case x: ActorRefRoutee       => x.ref.path.toString
        case x: ActorSelectionRoutee => x.selection.pathString
      }
      for (routee <- context.children) {
        val index = active.indexOf(routee.path.toStringWithoutAddress)
        if (index >= 0) {
          active.remove(index)
        } else {
          routee ! PoisonPill
        }
      }
      for (terminated <- active) {
        val name = terminated.substring(terminated.lastIndexOf("/") + 1)
        val child = context.actorOf(props, name)
        context.watch(child)
      }
    }

    case Terminated(child) => router ! GetRoutees
  }
}

case class PreferredSize(size: Int)

class WrongDynamicRouteeSizer(nrActors: Int, props: Props, router: ActorRef)
    extends Actor {
  var nrChildren = nrActors

  def createRoutee(nr: Int): Unit = {
    val child = context.actorOf(props, "routee-" + nr)
    router ! AddRoutee(ActorRefRoutee(child))
  }

  override def preStart(): Unit = {
    super.preStart()
    (0 until nrChildren).foreach(nr => createRoutee(nr))
  }

  override def receive: Receive = {
    case PreferredSize(size) => {
      if (size < nrChildren) {
        println("Delete %d children".format(nrChildren - size))
        context.children
          .take(nrChildren - size)
          .foreach(ref => {
            println("delete: " + ref)
            router ! RemoveRoutee(ActorRefRoutee(ref))
          })
        router ! GetRoutees
      } else {
        (nrChildren until size).foreach(nr => createRoutee(nr))
      }
      nrChildren = size
    }
    case routees: Routees => {
      import collection.JavaConverters._
      val active = routees.getRoutees.asScala.map {
        case x: ActorRefRoutee       => x.ref.path.toString
        case x: ActorSelectionRoutee => x.selection.pathString
      }
      println("Active: " + active)
      val notUsed = context.children.filterNot(
        routee => active.contains(routee.path.toString)
      )
      println("Not used: " + notUsed)
      notUsed.foreach(context.stop)
    }
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    println("restart %s".format(self.path.toString))
  }
}

class DynamicRouteeSizer2(nrActors: Int, props: Props, router: ActorRef)
    extends Actor {
  var nrChildren = nrActors;

  def createRoutee(nr: Int): Unit = {
    val child = context.actorOf(props, s"routee-$nr")
    val selection = context.actorSelection(child.path)
    router ! AddRoutee(ActorSelectionRoutee(selection))
    context.watch(child)
    println("Add routee " + child)
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    println("restart %s".format(self.path.toString))
  }

  override def preStart(): Unit = {
    super.preStart()
    (0 until nrChildren).foreach(createRoutee)
  }

  override def receive: Receive = {
    case PreferredSize(size) => {
      val currentNumber = context.children.size
      if (size < currentNumber) {
        println(s"Delete ${currentNumber - size} children.")
        context.children.take(currentNumber - size).foreach(ref => {
          println("delete: " + ref)
          context.stop(ref)
        })
      } else {
        (currentNumber until size).foreach(createRoutee)
      }
      nrChildren = size
    }

    case routees: Routees => {
      println("routees " + routees)
      if (routees.getRoutees.size < nrChildren) {
        (routees.getRoutees.size until nrChildren).foreach(createRoutee)
      }
    }

    case Terminated(child) => {
      println("Terminated " + child)
      val selection = context.actorSelection(child.path)
      router ! RemoveRoutee(ActorSelectionRoutee(selection))
      router ! GetRoutees
    }
  }
}

class GetLicenseCreator(nrActors: Int, nextStep: ActorRef) extends Actor {
  var createdActors = Seq[ActorRef]()

  override def preStart(): Unit = {
    super.preStart()
    createdActors = (0 until  nrActors).map(nr => {
      context.actorOf(Props(new GetLicense(nextStep)), "GetLicense"+nr)
    })
  }

  def receive = {
    case "KillFirst" => {
      createdActors.headOption.foreach(_ ! Kill)
      createdActors = createdActors.tail
    }
    case _ => throw new IllegalArgumentException("not supported")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    println("restart %s".format(self.path.toString))
  }
}

class GetLicenseCreator2(nrActors: Int, nextStep: ActorRef) extends Actor {
  //restart children
  override def preStart(): Unit = {
    super.preStart()
    (0 until  nrActors).map(nr => {
      val child = context.actorOf(Props(new GetLicense(nextStep)), "GetLicense"+nr)
      context.watch(child)
    })
  }

  def receive = {
    case "KillFirst" => {
      if(!context.children.isEmpty) {
        context.children.head ! PoisonPill
      }
    }
    case Terminated(child) => {
      val newChild = context.actorOf(Props(new GetLicense(nextStep)), child.path.name)
      context.watch(newChild)
    }
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    println("restart %s".format(self.path.toString))
  }
}

class TestSuper() extends Actor {
  def receive = {
    case "OK" =>
    case _ => throw new IllegalArgumentException("not supported")
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    println("restart %s".format(self.path.toString))
  }
}
