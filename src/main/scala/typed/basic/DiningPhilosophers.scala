package typed.basic

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors

object Chopstick {
  import Philosopher._

  sealed trait ChopstickProtocol
  final case class PutChopstick(philosopher: ActorRef[PhilosopherProtocol]) extends ChopstickProtocol
  final case class TakeChopstick(philosopher: ActorRef[PhilosopherProtocol]) extends ChopstickProtocol

  val chopstickAvailable: Behavior[ChopstickProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case TakeChopstick(philosopher) =>
        philosopher ! ChopstickTaken(ctx.self)
        chopstickTaken
      case _ => Behaviors.same
    }
  }

  val chopstickTaken: Behavior[ChopstickProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case TakeChopstick(otherPhilosopher) =>
        otherPhilosopher ! ChopstickBusy(ctx.self)
        Behaviors.same
      case PutChopstick(philosopher) => chopstickAvailable
      case _ => Behaviors.same
    }
  }
}

object Philosopher {
  import Chopstick._

  sealed trait PhilosopherProtocol
  object Eat extends PhilosopherProtocol
  object Think extends PhilosopherProtocol
  final case class ChopstickBusy(chopstick: ActorRef[ChopstickProtocol]) extends PhilosopherProtocol
  final case class ChopstickTaken(chopstick: ActorRef[ChopstickProtocol]) extends PhilosopherProtocol

}

object DiningPhilosophers {
  import Chopstick._

  final case class Start()

  val main: Behavior[Start] = Behaviors.setup { context =>
    val chopstick1 = context.spawn(chopstickAvailable, "chopstick-1")

    Behaviors.receiveMessage { msg =>
      chopstick1 ! TakeChopstick(philosopher = ???)
      Behaviors.same
    }
  }

  val system: ActorSystem[Start] = ActorSystem(main, "main")
  system ! Start()

}
