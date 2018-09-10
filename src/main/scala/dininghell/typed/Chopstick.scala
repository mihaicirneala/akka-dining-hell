package dininghell.typed

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object Chopstick {
  import Philosopher._

  sealed trait ChopstickProtocol
  final case class PutChopstick(philosopher: ActorRef[Philosopher.PhilosopherProtocol]) extends ChopstickProtocol
  final case class TakeChopstick(philosopher: ActorRef[Philosopher.PhilosopherProtocol]) extends ChopstickProtocol

  val available: Behavior[ChopstickProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case TakeChopstick(philosopher) =>
        philosopher ! ChopstickTaken(ctx.self)
        Chopstick.taken
      case _ => Behaviors.same
    }
  }

  val taken: Behavior[ChopstickProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case TakeChopstick(otherPhilosopher) =>
        otherPhilosopher ! ChopstickBusy(ctx.self)
        Behaviors.same
      case PutChopstick(seat) => Chopstick.available
      case _ => Behaviors.same
    }
  }
}
