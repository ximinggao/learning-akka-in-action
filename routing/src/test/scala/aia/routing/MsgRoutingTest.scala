package aia.routing
import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import scala.concurrent.duration._

class MsgRoutingTest
    extends TestKit(ActorSystem("MsgRoutingTest"))
    with WordSpecLike
    with BeforeAndAfterAll {
  override def afterAll(): Unit = {
    system.terminate()
  }

  "The Router" must {
    "routes depending on speed" in {
      val normalFlowProbe = TestProbe()
      val cleanupProbe = TestProbe()

      val router = system.actorOf(
        Props.empty.withRouter(
          SpeedRouterPool(
            50,
            Props(new RedirectActor(normalFlowProbe.ref)),
            Props(new RedirectActor(cleanupProbe.ref))
          )
        )
      )

      val msg = Photo(license = "123xyz", speed = 60)
      router ! msg
      cleanupProbe.expectNoMessage(1 second)
      normalFlowProbe.expectMsg(msg)

      val msg2 = Photo(license = "123xyz", speed = 45)
      router ! msg2
      cleanupProbe.expectMsg(msg2)
      normalFlowProbe.expectNoMessage(1 second)
    }
  }
}
