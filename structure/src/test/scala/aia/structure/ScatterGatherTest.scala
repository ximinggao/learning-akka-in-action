package aia.structure
import java.util.Date

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._

class ScatterGatherTest
    extends TestKit(ActorSystem("ScatterGatherTest"))
    with WordSpecLike
    with BeforeAndAfterAll {
  val timeout = 2 seconds

  override def afterAll(): Unit = {
    system.terminate()
  }

  "The ScatterGather" must {
    "scatter the message and gather them again" in {
      val endProbe = TestProbe()
      val aggragateRef = system.actorOf(Props(new Aggregator(timeout, endProbe.ref)))
      val speedRef = system.actorOf(Props(new GetSpeed(aggragateRef)))
      val timeRef = system.actorOf(Props(new GetTime(aggragateRef)))
      val recipientsRef = system.actorOf(Props(new RecipientList(Seq(speedRef, timeRef))))

      val photoDate = new Date()
      val photoSpeed = 60
      val msg = PhotoMessage("id1", ImageProcessing.createPhotoString(photoDate, photoSpeed))

      recipientsRef ! msg

      val combinedMsg = PhotoMessage(msg.id, msg.photo, Some(photoDate), Some(photoSpeed))

      endProbe.expectMsg(combinedMsg)
    }
  }
}
