package basic.typed

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._


case class TableSeat(philosopher: ActorRef[Philosopher.PhilosopherProtocol],
                     leftChopstick: ActorRef[Chopstick.ChopstickProtocol],
                     rightChopstick: ActorRef[Chopstick.ChopstickProtocol])

object Philosopher {
  import Chopstick._

  sealed trait PhilosopherProtocol
  final case class Eat(seat: TableSeat) extends PhilosopherProtocol
  final case class Think(seat: TableSeat) extends PhilosopherProtocol
  final case class ChopstickBusy(chopstick: ActorRef[ChopstickProtocol], seat: TableSeat) extends PhilosopherProtocol
  final case class ChopstickTaken(chopstick: ActorRef[ChopstickProtocol], seat: TableSeat) extends PhilosopherProtocol

  //When a philosopher is thinking it can become hungry
  //and try to pick up its chopsticks and eat
  val thinking: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case Eat(seat) =>
        seat.leftChopstick ! TakeChopstick(seat)
        seat.rightChopstick ! TakeChopstick(seat)
        hungry
      case _ => Behaviors.same
    }
  }

  //When a philosopher is hungry it tries to pick up its chopsticks and eat
  //When it picks one up, it goes into wait for the other
  //If the philosophers first attempt at grabbing a chopstick fails,
  //it starts to wait for the response of the other grab
  val hungry: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case ChopstickTaken(chopstick, seat) =>
        waitingFor
      case ChopstickBusy(chopstick, seat) =>
        deniedChopstick
      case _ => Behaviors.same
    }
  }
  //When a philosopher is waiting for the last chopstick it can either obtain it
  //and start eating, or the other chopstick was busy, and the philosopher goes
  //back to think about how he should obtain his chopsticks :-)
  val waitingFor: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case ChopstickTaken(chopstickToWaitFor, seat) =>
        println(s"🥢  ${seat.philosopher.path.name} has picked up ${seat.leftChopstick.path.name} and ${seat.rightChopstick.path.name} and starts to eat")
        ctx.schedule(5.seconds, ctx.self, Think(seat))
        eating
      case ChopstickBusy(chopstick, seat) =>
        val otherChopstick = if (seat.leftChopstick == chopstick) seat.rightChopstick else seat.leftChopstick
        otherChopstick ! PutChopstick(seat)
        ctx.schedule(10.milliseconds, ctx.self, Eat(seat))
        thinking
      case _ => Behaviors.same
    }
  }

  //When the results of the other grab comes back,
  //he needs to put it back if he got the other one.
  //Then go back and think and try to grab the chopsticks again
  val deniedChopstick: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case ChopstickTaken(chopstick, seat) =>
        chopstick ! PutChopstick(seat)
        ctx.schedule(10.milliseconds, ctx.self, Eat(seat))
        thinking
      case ChopstickBusy(chopstick, seat) =>
        ctx.schedule(10.milliseconds, ctx.self, Eat(seat))
        thinking
      case _ => Behaviors.same
    }
  }

  //When a philosopher is eating, he can decide to start to think,
  //then he puts down his chopsticks and starts to think
  val eating: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case Think(seat) =>
        seat.leftChopstick ! PutChopstick(seat)
        seat.rightChopstick ! PutChopstick(seat)
        println(s"🥢  ${seat.philosopher.path.name} puts down his chopsticks and starts to think")
        ctx.schedule(5.seconds, ctx.self, Eat(seat))
        thinking
      case _ => Behaviors.same
    }
  }

  val idle: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case Think(seat) =>
        println(s"🥢  ${seat.philosopher.path.name} starts to think")
        ctx.schedule(5.seconds, ctx.self, Eat(seat))
        thinking
      case _ => Behaviors.same
    }
  }

}
