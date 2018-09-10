package dininghell.typed

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._

object Creator {

  sealed trait CreatorProtocol
  final case class StartSimulation() extends CreatorProtocol
  final case class Bomb(devil: ActorRef[Devil.DevilProtocol]) extends CreatorProtocol

  val tableSize = 5

  private val restartStrategy = SupervisorStrategy.restart

  val creating: Behavior[CreatorProtocol] = Behaviors.setup { context =>
    //Create philosophers and assign them their left and right chopstick
    val chopsticks = for (i <- 1 to tableSize) yield context.spawn(Chopstick.available, "Chopstick-" + i)
    val philosophers = for (i <- 1 to tableSize) yield {
      context.spawn(Behaviors.supervise(Philosopher.idle).onFailure(SupervisorStrategy.restart), "Philosopher-" + i)
    }
    val seats = for (i <- 0 until tableSize) yield {
      TableSeat(philosophers(i), chopsticks(i), chopsticks((i + 1) % tableSize))
    }
    val devil = context.spawn(Devil.evil, "Devil")
    Behaviors.receiveMessage {
      case StartSimulation() =>
        seats.foreach { seat =>
          seat.philosopher ! Philosopher.Start(seat)
          devil ! Devil.PhilosopherCreated(seat.philosopher)
        }
        devil ! Devil.StartTheEvil(context.self)
        Behaviors.same
      case Bomb(devil) =>
        val phIndex = 1 + (new scala.util.Random).nextInt(tableSize)
        println(s"💣 Creator gets a bomb and throws it to Philosopher-$phIndex")
        context.child("Philosopher-" + phIndex).foreach { child =>
          val philosopher = child.asInstanceOf[ActorRef[Philosopher.PhilosopherProtocol]]
          context.schedule(3.seconds, philosopher, Philosopher.Bomb(devil, context.self))
        }
        Behaviors.same
    }
  }
}

