package dining.typed

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors

object Dining {
  final case class StartSimulation()

  val tableSize = 5

  def main(args: Array[String]): Unit = {
    val creator: Behavior[StartSimulation] = Behaviors.setup { context =>
      //Create philosophers and assign them their left and right chopstick
      val chopsticks = for (i <- 1 to tableSize) yield context.spawn(Chopstick.available, "Chopstick-" + i)
      val philosophers = for (i <- 1 to tableSize) yield context.spawn(Philosopher.idle, "Philosopher-" + i)
      val seats = for (i <- 0 until tableSize) yield {
        TableSeat(philosophers(i), chopsticks(i), chopsticks((i + 1) % tableSize))
      }
      //Signal all philosophers that they should start thinking, and watch the show
      Behaviors.receiveMessage { _ =>
        seats.foreach { seat =>
          seat.philosopher ! Philosopher.Think(seat)
        }
        Behaviors.same
      }
    }

    val system: ActorSystem[StartSimulation] = ActorSystem(creator, "creator")
    system ! StartSimulation()
  }
}
