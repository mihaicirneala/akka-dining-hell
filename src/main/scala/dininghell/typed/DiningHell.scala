package dininghell.typed

import akka.actor.typed._

object DiningHell {

  def main(args: Array[String]): Unit = {
    val system: ActorSystem[God.StartSimulation] = ActorSystem(God.creating, "God")
    system ! God.StartSimulation()
  }
}
