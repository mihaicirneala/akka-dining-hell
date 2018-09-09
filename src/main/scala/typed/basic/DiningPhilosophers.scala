package typed.basic

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors

/*
* First we define our message protocols
*/
sealed trait ChopstickProtocol

final case class Put(philosopher: ActorRef[PhilosopherProtocol]) extends ChopstickProtocol
final case class Take(philosopher: ActorRef[PhilosopherProtocol]) extends ChopstickProtocol

sealed trait PhilosopherProtocol

object Eat extends PhilosopherProtocol
object Think extends PhilosopherProtocol
final case class Busy(chopstick: ActorRef[ChopstickProtocol]) extends PhilosopherProtocol
final case class Taken(chopstick: ActorRef[ChopstickProtocol]) extends PhilosopherProtocol


object DiningPhilosophers {

  val chopstickAvailable: Behavior[ChopstickProtocol] = Behaviors.receive { (ctx, msg) => msg match {
      case Take(philosopher) =>
        philosopher ! Taken(ctx.self)
        chopstickTaken
      case _ => Behaviors.same
    }
  }

  val chopstickTaken: Behavior[ChopstickProtocol] = Behaviors.receive { (ctx, msg) => msg match {
      case Take(otherPhilosopher) =>
        otherPhilosopher ! Busy(ctx.self)
        Behaviors.same
      case Put(philosopher) => chopstickAvailable
      case _ => Behaviors.same
    }
  }

  final case class Start()

  val main: Behavior[Start] = Behaviors.setup { context =>
    val chopstick1 = context.spawn(chopstickAvailable, "chopstick-1")

    Behaviors.receiveMessage { msg =>
      chopstick1 ! Take(philosopher = ???)
      Behaviors.same
    }
  }

  val system: ActorSystem[Start] = ActorSystem(main, "main")
  system ! Start()

}
