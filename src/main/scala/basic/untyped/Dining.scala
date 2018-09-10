package basic.untyped

import akka.actor._

import scala.concurrent.duration._

// Akka adaptation of: http://www.dalnefre.com/wp/2010/08/dining-philosophers-in-humus/
// Inspired by: https://github.com/akka/akka-samples/blob/2.5/akka-sample-fsm-scala/src/main/scala/sample/become/DiningHakkersOnBecome.scala

object Dining {
  val tableSize = 5
  val system = ActorSystem()

  def main(args: Array[String]): Unit = {
    //Create 5 philosophers and assign them their left and right chopstick
    val chopsticks = for (i <- 1 to tableSize) yield system.actorOf(Props[Chopstick], "Chopstick-" + i)
    val philosophers = for (i <- chopsticks.indices)  yield {
      system.actorOf(Props(classOf[Philosopher], "Philosopher-" + (i+1), chopsticks(i), chopsticks((i + 1) % tableSize)))
    }

    //Signal all philosophers that they should start thinking, and watch the show
    philosophers.foreach(_ ! Philosopher.Think)
  }
}

