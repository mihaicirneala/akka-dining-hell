package dininghell.typed

import akka.actor.typed._

object DiningHell {

  def main(args: Array[String]): Unit = {
    val system: ActorSystem[Creator.StartSimulation] = ActorSystem(Creator.creating, "creator")
    system ! Creator.StartSimulation()
  }
}
