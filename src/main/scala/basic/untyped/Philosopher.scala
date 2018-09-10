package basic.untyped

import akka.actor.{Actor, ActorRef}

import scala.concurrent.duration._


object Philosopher {
  sealed trait PhilosopherProtocol
  final case class Busy(chopstick: ActorRef) extends PhilosopherProtocol
  final case class Taken(chopstick: ActorRef) extends PhilosopherProtocol
  object Eat extends PhilosopherProtocol
  object Think extends PhilosopherProtocol
}

/*
* A philosopher is a person who either thinks about the universe or has to eat
*/
class Philosopher(name: String, left: ActorRef, right: ActorRef) extends Actor {

  import Philosopher._
  import context._

  //When a philosopher is thinking it can become hungry
  //and try to pick up its chopsticks and eat
  def thinking: Receive = {
    case Eat =>
      become(hungry)
      left ! Chopstick.Take(self)
      right ! Chopstick.Take(self)
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
      println(s"🥢  $name has picked up ${left.path.name} and ${right.path.name} and starts to eat")
      become(eating)
      system.scheduler.scheduleOnce(5.seconds, self, Think)

    case Busy(chopstick) =>
      otherChopstick ! Chopstick.Put(self)
      startThinking(10.milliseconds)
  }

  //When the results of the other grab comes back,
  //he needs to put it back if he got the other one.
  //Then go back and think and try to grab the chopsticks again
  def denied_a_chopstick: Receive = {
    case Taken(chopstick) =>
      chopstick ! Chopstick.Put(self)
      startThinking(10.milliseconds)
    case Busy(chopstick) =>
      startThinking(10.milliseconds)
  }

  //When a philosopher is eating, he can decide to start to think,
  //then he puts down his chopsticks and starts to think
  def eating: Receive = {
    case Think =>
      left ! Chopstick.Put(self)
      right ! Chopstick.Put(self)
      println(s"🥢  $name puts down his chopsticks and starts to think")
      startThinking(5.seconds)
  }

  //All philosophers start in a non-eating state
  def receive = {
    case Think =>
      println(s"🥢  $name starts to think")
      startThinking(5.seconds)
  }

  private def startThinking(duration: FiniteDuration): Unit = {
    become(thinking)
    system.scheduler.scheduleOnce(duration, self, Eat)
  }
}
