package untyped.basic

import akka.actor._
import scala.concurrent.duration._

// Akka adaptation of
// http://www.dalnefre.com/wp/2010/08/dining-philosophers-in-humus/

/*
* First we define our messages, they basically speak for themselves
*/
sealed trait DiningPhilosopherMessage
final case class Busy(chopstick: ActorRef) extends DiningPhilosopherMessage
final case class Put(philosopher: ActorRef) extends DiningPhilosopherMessage
final case class Take(philosopher: ActorRef) extends DiningPhilosopherMessage
final case class Taken(chopstick: ActorRef) extends DiningPhilosopherMessage
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
}


object DiningPhilosophersUntyped {
  val system = ActorSystem()

  def main(args: Array[String]): Unit = run()

  def run(): Unit = {
    //Create 5 chopsticks
    val chopsticks = for (i <- 1 to 5) yield system.actorOf(Props[Chopstick], "Chopstick" + i)

    //Create 5 philosophers and assign them their left and right chopstick
    val philosophers = for {
      (name, i) <- List("Ghosh", "Boner", "Klang", "Krasser", "Manie").zipWithIndex
    } yield system.actorOf(Props(classOf[Philosopher], name, chopsticks(i), chopsticks((i + 1) % 5)))

    //Signal all philosophers that they should start thinking, and watch the show
    philosophers.foreach(_ ! Think)
  }
}
