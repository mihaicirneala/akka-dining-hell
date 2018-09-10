package supervision.typed

import akka.actor.typed._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import org.joda.time.DateTime

import scala.concurrent.duration._

case class TableSeat(philosopher: ActorRef[Philosopher.PhilosopherProtocol],
                     leftChopstick: ActorRef[Chopstick.ChopstickProtocol],
                     rightChopstick: ActorRef[Chopstick.ChopstickProtocol])

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

object Philosopher {
  import Chopstick._

  sealed trait PhilosopherProtocol
  final case class Start(seat: TableSeat) extends PhilosopherProtocol
  object Eat extends PhilosopherProtocol
  object Think extends PhilosopherProtocol
  final case class ChopstickBusy(chopstick: ActorRef[ChopstickProtocol]) extends PhilosopherProtocol
  final case class ChopstickTaken(chopstick: ActorRef[ChopstickProtocol]) extends PhilosopherProtocol
  final case class Bomb(devil: ActorRef[Devil.DevilProtocol], creator: ActorRef[Creator.CreatorProtocol]) extends PhilosopherProtocol

  private def handleBomb(name: String, devil: ActorRef[Devil.DevilProtocol], creator: ActorRef[Creator.CreatorProtocol]): Unit = {
    if (Devil.isBadLuck) {
      println(s"💣 Bomb exploded at $name")
      throw new RuntimeException(s"Bomb exploded at $name")
    } else {
      println(s"💣 $name throws the bomb back to Creator")
      creator ! Creator.Bomb(devil)
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

object Devil {
  sealed trait DevilProtocol
  final case class PhilosopherCreated(philosopher: ActorRef[Philosopher.PhilosopherProtocol]) extends DevilProtocol
  final case class StartTheEvil(creator: ActorRef[Creator.CreatorProtocol]) extends DevilProtocol
  final case class ThrowNewBomb(creator: ActorRef[Creator.CreatorProtocol]) extends DevilProtocol
  final case class BombExploded(philosopher: ActorRef[Philosopher.PhilosopherProtocol]) extends DevilProtocol

  val bombTimeout = 15
  val bombChances = 4

  val evil: Behavior[DevilProtocol] = Behaviors.setup(new Devil(_))

  def isBadLuck: Boolean = {
    val firstRandom = (new scala.util.Random).nextInt(bombChances)
    val secondRandom = (new scala.util.Random).nextInt(bombChances)
    firstRandom == secondRandom
  }
}

class Devil(ctx: ActorContext[Devil.DevilProtocol]) extends MutableBehavior[Devil.DevilProtocol] {
  import Devil._

  var circulatingBomb = false
  var bombThrowedAt = DateTime.now()

  override def onMessage(msg: Devil.DevilProtocol): Behavior[Devil.DevilProtocol] = {
    msg match {
      case PhilosopherCreated(philosopher) =>
        ctx.watchWith(philosopher, BombExploded(philosopher)) // TODO: fix this. watching doesn't actually work
        this
      case StartTheEvil(creator) =>
        scheduleBombThrowing(creator)
        this
      case ThrowNewBomb(creator) =>
        if (!circulatingBomb) {
          bombThrowedAt = DateTime.now()
          println(s"💣 Devil is throwing a new bomb")
          creator ! Creator.Bomb(ctx.self)
          circulatingBomb = true
        } else if (bombThrowedAt.plusSeconds(bombTimeout).getMillis < DateTime.now.getMillis) {
          println(s"💣 Devil's bomb probably got lost")
          circulatingBomb = false
        }
        scheduleBombThrowing(creator)
        this
      case BombExploded(philosopher) =>
        println(s"💣 Devil got explosion confirmation in hands of ${philosopher.path.name}")
        circulatingBomb = false
        this
      case _ => this
    }
  }

  private def scheduleBombThrowing(creator: ActorRef[Creator.CreatorProtocol]): Unit = {
//    val random = 7 + (new scala.util.Random).nextInt(15)
    ctx.schedule(2.seconds, ctx.self, ThrowNewBomb(creator))
  }
}

object DiningPhilosophers {

  def main(args: Array[String]): Unit = {
    val system: ActorSystem[Creator.StartSimulation] = ActorSystem(Creator.creating, "creator")
    system ! Creator.StartSimulation()
  }
}
