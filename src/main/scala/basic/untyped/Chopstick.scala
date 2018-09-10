package basic.untyped

import akka.actor.{Actor, ActorRef}


object Chopstick {
  sealed trait ChopstickProtocol
  final case class Put(philosopher: ActorRef) extends ChopstickProtocol
  final case class Take(philosopher: ActorRef) extends ChopstickProtocol
}

/*
* A Chopstick is an actor, it can be taken, and put back
*/
class Chopstick extends Actor {

  import Chopstick._
  import context._

  //When a Chopstick is taken by a philosopher
  //It will refuse to be taken by other philosophers
  //But the owning philosopher can put it back
  def takenBy(philosopher: ActorRef): Receive = {
    case Take(otherPhilosopher) =>
      otherPhilosopher ! Philosopher.Busy(self)
    case Put(`philosopher`) =>
      become(available)
  }

  //When a Chopstick is available, it can be taken by a philosopher
  def available: Receive = {
    case Take(philosopher) =>
      become(takenBy(philosopher))
      philosopher ! Philosopher.Taken(self)
  }

  //A Chopstick begins its existence as available
  def receive = available
}
