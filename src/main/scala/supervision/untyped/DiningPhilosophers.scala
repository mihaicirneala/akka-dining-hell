package supervision.untyped

import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor._

import scala.concurrent.duration._

// Akka adaptation of
// http://www.dalnefre.com/wp/2010/08/dining-philosophers-in-humus/

/*
* First we define our messages, they basically speak for themselves
*/
sealed trait DiningPhilosopherMessage

final case class Busy(chopstick: ActorRef) extends DiningPhilosopherMessage
final case class Taken(chopstick: ActorRef) extends DiningPhilosopherMessage

final case class Put(philosopher: ActorRef) extends DiningPhilosopherMessage
final case class Take(philosopher: ActorRef) extends DiningPhilosopherMessage

object Eat extends DiningPhilosopherMessage
object Think extends DiningPhilosopherMessage

/*
* A Chopstick is an actor, it can be taken, and put back
*/
class Chopstick extends Actor {

  import context._

  //When a Chopstick is taken by a philosopher
  //It will refuse to be taken by other philosophers
  //But the owning philosopher can put it back
  def takenBy(philosopher: ActorRef): Receive = {
    case Take(otherPhilosopher) =>
      otherPhilosopher ! Busy(self)
    case Put(`philosopher`) =>
      become(available)
  }

  //When a Chopstick is available, it can be taken by a philosopher
  def available: Receive = {
    case Take(philosopher) =>
      become(takenBy(philosopher))
      philosopher ! Taken(self)
  }

  //A Chopstick begins its existence as available
  def receive = available
}

/*
* A philosopher is a person who either thinks about the universe or has to eat
*/
class Philosopher(name: String, left: ActorRef, right: ActorRef) extends Actor {

  import context._

  //When a philosopher is thinking it can become hungry
  //and try to pick up its chopsticks and eat
  def thinking: Receive = {
    case Eat =>
      become(hungry)
      left ! Take(self)
      right ! Take(self)
  }

  //When a philosopher is hungry it tries to pick up its chopsticks and eat
  //When it picks one up, it goes into wait for the other
  //If the philosophers first attempt at grabbing a chopstick fails,
  //it starts to wait for the response of the other grab
  def hungry: Receive = {
    case Taken(`left`) =>
      become(waiting_for(right, left))
    case Taken(`right`) =>
      become(waiting_for(left, right))
    case Busy(chopstick) =>
      become(denied_a_chopstick)
  }

  //When a philosopher is waiting for the last chopstick it can either obtain it
  //and start eating, or the other chopstick was busy, and the philosopher goes
  //back to think about how he should obtain his chopsticks :-)
  def waiting_for(chopstickToWaitFor: ActorRef, otherChopstick: ActorRef): Receive = {
    case Taken(`chopstickToWaitFor`) =>
      println("%s has picked up %s and %s and starts to eat".format(name, left.path.name, right.path.name))
      become(eating)
      system.scheduler.scheduleOnce(5.seconds, self, Think)

    case Busy(chopstick) =>
      otherChopstick ! Put(self)
      startThinking(10.milliseconds)
  }

  //When the results of the other grab comes back,
  //he needs to put it back if he got the other one.
  //Then go back and think and try to grab the chopsticks again
  def denied_a_chopstick: Receive = {
    case Taken(chopstick) =>
      chopstick ! Put(self)
      startThinking(10.milliseconds)
    case Busy(chopstick) =>
      startThinking(10.milliseconds)
  }

  //When a philosopher is eating, he can decide to start to think,
  //then he puts down his chopsticks and starts to think
  def eating: Receive = {
    case Think =>
      left ! Put(self)
      right ! Put(self)
      println("%s puts down his chopsticks and starts to think".format(name))
      startThinking(5.seconds)
  }

  //All philosophers start in a non-eating state
  def receive = {
    case Think =>
      println("%s starts to think".format(name))
      startThinking(5.seconds)
  }

  private def startThinking(duration: FiniteDuration): Unit = {
    become(thinking)
    system.scheduler.scheduleOnce(duration, self, Eat)
  }

  override def postRestart(reason: Throwable) {
    self ! Think
    super.postRestart(reason)
  }
}

object Creator {
  sealed trait CreatorProtocol
  object StartSimulation extends CreatorProtocol
}

class Creator(tableSize: Int) extends Actor {
//  override val supervisorStrategy =
//    OneForOneStrategy() {
//      case _: ActorInitializationException ⇒ Stop
//      case _: ActorKilledException         ⇒ Restart
//      case _: DeathPactException           ⇒ Stop
//      case _: Exception                    ⇒ Restart
//    }

  import Creator._
  import context._

  private val devil = context.actorOf(Props[Devil], "Devil")
  private val chopsticks = for (i <- 1 to tableSize) yield context.actorOf(Props[Chopstick], "Chopstick-" + i)
  //Create 5 philosophers and assign them their left and right chopstick
  private def createPhilospher(i: Int) = context.actorOf(Props(classOf[Philosopher], "Philosopher-" + i, chopsticks(i), chopsticks((i + 1) % tableSize)), "Philosopher-" + i)
  private var philosophers = for (i <- chopsticks.indices)  yield createPhilospher(i)

  def receive: Receive = {
    case StartSimulation =>

      //Signal all philosophers that they should start thinking, and watch the show
      philosophers.foreach { philosopher =>
        context.watch(philosopher)
        philosopher ! Think
        devil ! Devil.PhilosopherCreated(philosopher)
      }
      devil ! Devil.StartTheEvil
    case Terminated(philosopher) =>
      val index = philosophers.indexOf(philosopher)
      chopsticks(index) ! Put(philosopher)
      chopsticks((index + 1) % tableSize) ! Put(philosopher)
      val newPhilosopher = createPhilospher(index)
      philosophers = philosophers.updated(index, newPhilosopher)
      newPhilosopher ! Think
      devil ! Devil.PhilosopherCreated(newPhilosopher)
  }
}

object Devil {
  sealed trait DevilProtocol
  final case class PhilosopherCreated(philosopher: ActorRef) extends DevilProtocol
  object StartTheEvil extends DevilProtocol
  object KillRandomPhilosopher extends DevilProtocol
}

class Devil extends Actor {

  import Devil._
  import context._

  var philosophers = Set.empty[ActorRef]

  def receive: Receive = {
    case PhilosopherCreated(philosopher) =>
      println(s"Devil.PhilosopherCreated(${philosopher.path.name})")
      philosophers += philosopher
    case StartTheEvil =>
      scheduleKill()
    case KillRandomPhilosopher =>
      if (philosophers.nonEmpty) {
        val philIndex = (new scala.util.Random).nextInt(philosophers.size)
        val theUnfortunate = philosophers.toSeq(philIndex)
        println(s"Devil kills ${theUnfortunate.path.name}")
        theUnfortunate ! Kill
        philosophers = philosophers.filterNot(_ == theUnfortunate)
      }
      scheduleKill()
  }

  private def scheduleKill() = {
    val random = 7 + (new scala.util.Random).nextInt(15)
    system.scheduler.scheduleOnce(random.seconds, self, KillRandomPhilosopher)
  }
}

object DiningPhilosophers {
  val tableSize = 5
  val system = ActorSystem()

  def main(args: Array[String]): Unit = {
    val creator = system.actorOf(Props(classOf[Creator], tableSize), "Creator")
    creator ! Creator.StartSimulation
  }
}

