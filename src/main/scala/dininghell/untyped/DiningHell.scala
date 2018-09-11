package dininghell.untyped

import akka.actor._

object DiningHell {
  val tableSize = 5
  val system = ActorSystem()

  def main(args: Array[String]): Unit = {
    val creator = system.actorOf(Props(classOf[God], tableSize), "God")
    creator ! God.StartSimulation
  }
}

