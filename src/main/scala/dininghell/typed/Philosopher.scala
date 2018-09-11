package dininghell.typed

import akka.actor.typed.{ActorRef, Behavior, PostStop, PreRestart, Signal}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.concurrent.duration._


case class TableSeat(philosopher: ActorRef[Philosopher.PhilosopherProtocol],
                     leftChopstick: ActorRef[Chopstick.ChopstickProtocol],
                     rightChopstick: ActorRef[Chopstick.ChopstickProtocol])


object Philosopher {
  import Chopstick._

  sealed trait PhilosopherProtocol
  final case class Start(seat: TableSeat) extends PhilosopherProtocol
  object Eat extends PhilosopherProtocol
  object Think extends PhilosopherProtocol
  final case class ChopstickBusy(chopstick: ActorRef[ChopstickProtocol]) extends PhilosopherProtocol
  final case class ChopstickTaken(chopstick: ActorRef[ChopstickProtocol]) extends PhilosopherProtocol
  final case class Bomb(devil: ActorRef[Devil.DevilProtocol], creator: ActorRef[God.CreatorProtocol]) extends PhilosopherProtocol

  private def handleBomb(name: String, devil: ActorRef[Devil.DevilProtocol], creator: ActorRef[God.CreatorProtocol]): Unit = {
    if (Devil.isBadLuck) {
      println(s"💣 Bomb exploded at $name")
      throw new RuntimeException(s"Bomb exploded at $name")
    } else {
      println(s"💣 $name throws the bomb back to Creator")
      creator ! God.Bomb(devil)
    }
  }

  private def handleSignal(seat: TableSeat): PartialFunction[(ActorContext[PhilosopherProtocol], Signal), Behavior[PhilosopherProtocol]] = {

    case (ctx, PreRestart) =>
      //ctx.system.log.info("Worker {} is RESTARTED, count {}", ctx.self)
      seat.leftChopstick ! PutChopstick(seat.philosopher)
      seat.rightChopstick ! PutChopstick(seat.philosopher)
      seat.philosopher ! Start(seat)
      Behaviors.same
    case (_, PostStop) => Behaviors.same
  }

  //When a philosopher is thinking it can become hungry
  //and try to pick up its chopsticks and eat
  def thinking(seat: TableSeat): Behavior[PhilosopherProtocol] = Behaviors.receive[PhilosopherProtocol] { (ctx, msg) =>
    msg match {
      case Bomb(devil, creator) => handleBomb(ctx.self.path.name, devil, creator)
        Behaviors.same
      case Eat =>
        seat.leftChopstick ! TakeChopstick(seat.philosopher)
        seat.rightChopstick ! TakeChopstick(seat.philosopher)
        hungry(seat)
      case _ => Behaviors.same
    }
  } receiveSignal handleSignal(seat)

  //When a philosopher is hungry it tries to pick up its chopsticks and eat
  //When it picks one up, it goes into wait for the other
  //If the philosophers first attempt at grabbing a chopstick fails,
  //it starts to wait for the response of the other grab
  def hungry(seat: TableSeat): Behavior[PhilosopherProtocol] = Behaviors.receive[PhilosopherProtocol] { (ctx, msg) =>
    msg match {
      case Bomb(devil, creator) => handleBomb(ctx.self.path.name, devil, creator)
        Behaviors.same
      case ChopstickTaken(chopstick) =>
        waitingFor(seat)
      case ChopstickBusy(chopstick) =>
        deniedChopstick(seat)
      case _ => Behaviors.same
    }
  } receiveSignal handleSignal(seat)
  //When a philosopher is waiting for the last chopstick it can either obtain it
  //and start eating, or the other chopstick was busy, and the philosopher goes
  //back to think about how he should obtain his chopsticks :-)
  def waitingFor(seat: TableSeat): Behavior[PhilosopherProtocol] = Behaviors.receive[PhilosopherProtocol] { (ctx, msg) =>
    msg match {
      case Bomb(devil, creator) => handleBomb(ctx.self.path.name, devil, creator)
        Behaviors.same
      case ChopstickTaken(chopstickToWaitFor) =>
        println(s"🥢  ${seat.philosopher.path.name} has picked up ${seat.leftChopstick.path.name} and ${seat.rightChopstick.path.name} and starts to eat")
        ctx.schedule(5.seconds, ctx.self, Think)
        eating(seat)
      case ChopstickBusy(chopstick) =>
        val otherChopstick = if (seat.leftChopstick == chopstick) seat.rightChopstick else seat.leftChopstick
        otherChopstick ! PutChopstick(seat.philosopher)
        ctx.schedule(10.milliseconds, ctx.self, Eat)
        thinking(seat)
      case _ => Behaviors.same
    }
  } receiveSignal handleSignal(seat)

  //When the results of the other grab comes back,
  //he needs to put it back if he got the other one.
  //Then go back and think and try to grab the chopsticks again
  def deniedChopstick(seat: TableSeat): Behavior[PhilosopherProtocol] = Behaviors.receive[PhilosopherProtocol] { (ctx, msg) =>
    msg match {
      case Bomb(devil, creator) => handleBomb(ctx.self.path.name, devil, creator)
        Behaviors.same
      case ChopstickTaken(chopstick) =>
        chopstick ! PutChopstick(seat.philosopher)
        ctx.schedule(10.milliseconds, ctx.self, Eat)
        thinking(seat)
      case ChopstickBusy(chopstick) =>
        ctx.schedule(10.milliseconds, ctx.self, Eat)
        thinking(seat)
      case _ => Behaviors.same
    }
  } receiveSignal handleSignal(seat)

  //When a philosopher is eating, he can decide to start to think,
  //then he puts down his chopsticks and starts to think
  def eating(seat: TableSeat): Behavior[PhilosopherProtocol] = Behaviors.receive[PhilosopherProtocol] { (ctx, msg) =>
    msg match {
      case Bomb(devil, creator) => handleBomb(ctx.self.path.name, devil, creator)
        Behaviors.same
      case Think =>
        seat.leftChopstick ! PutChopstick(seat.philosopher)
        seat.rightChopstick ! PutChopstick(seat.philosopher)
        println(s"🥢  ${seat.philosopher.path.name} puts down his chopsticks and starts to think")
        ctx.schedule(5.seconds, ctx.self, Eat)
        thinking(seat)
      case _ => Behaviors.same
    }
  } receiveSignal handleSignal(seat)

  val idle: Behavior[PhilosopherProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case Bomb(devil, creator) => handleBomb(ctx.self.path.name, devil, creator)
        Behaviors.same
      case Start(seat) =>
        println(s"🥢  ${seat.philosopher.path.name} starts to think")
        ctx.schedule(5.seconds, ctx.self, Eat)
        thinking(seat)
      case _ => Behaviors.same
    }
  }

}

