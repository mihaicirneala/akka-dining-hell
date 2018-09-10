package dininghell.untyped

import akka.actor.{Actor, ActorInitializationException, ActorKilledException, DeathPactException, OneForOneStrategy, Props}
import akka.actor.SupervisorStrategy.{Restart, Stop}
import scala.concurrent.duration._


object Creator {
  sealed trait CreatorProtocol
  object StartSimulation extends CreatorProtocol
  object Bomb extends CreatorProtocol
}

class Creator(tableSize: Int) extends Actor {
  override val supervisorStrategy =
    OneForOneStrategy() {
      case _: ActorInitializationException ⇒ Stop
      case _: ActorKilledException         ⇒ Restart
      case _: DeathPactException           ⇒ Stop
      case _: Exception                    ⇒ Restart
    }

  import Creator._
  import context._

  def receive: Receive = {
    case StartSimulation =>
      val devil = context.actorOf(Props[Devil], "Devil")
      val chopsticks = for (i <- 1 to tableSize) yield context.actorOf(Props[Chopstick], "Chopstick-" + i)
      //Create 5 philosophers and assign them their left and right chopstick
      val philosophers = for (i <- chopsticks.indices)  yield {
        context.actorOf(Props(classOf[Philosopher], "Philosopher-" + i, chopsticks(i), chopsticks((i + 1) % tableSize)), "Philosopher-" + i)
      }

      //Signal all philosophers that they should start thinking, and watch the show
      philosophers.foreach { philosopher =>
        context.watch(philosopher)
        philosopher ! Philosopher.Think
        devil ! Devil.PhilosopherCreated(philosopher)
      }
      devil ! Devil.StartTheEvil
    case Creator.Bomb =>
      val phIndex = 1 + (new scala.util.Random).nextInt(tableSize)
      println(s"💣 Creator gets a bomb and throws it to Philosopher-$phIndex")
      context.child("Philosopher-" + phIndex).foreach { philosopher =>
        system.scheduler.scheduleOnce(3.seconds, philosopher, Philosopher.Bomb)
      }
  }
}
