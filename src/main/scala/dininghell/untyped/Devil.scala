package dininghell.untyped

import akka.actor.{Actor, ActorRef}
import org.joda.time.DateTime
import scala.concurrent.duration._


object Devil {
  sealed trait DevilProtocol
  final case class PhilosopherCreated(philosopher: ActorRef) extends DevilProtocol
  object StartTheEvil extends DevilProtocol
  final case class  ThrowNewBomb(creator: ActorRef) extends DevilProtocol
  final case class BombExploded(philosopher: ActorRef) extends DevilProtocol

  val bombTimeout = 15
  val bombChances = 4

  def isBadLuck: Boolean = {
    val firstRandom = (new scala.util.Random).nextInt(bombChances)
    val secondRandom = (new scala.util.Random).nextInt(bombChances)
    firstRandom == secondRandom
  }
}

class Devil extends Actor {

  import Devil._
  import context._

  var circulatingBomb = false
  var bombThrowedAt = DateTime.now()

  def receive: Receive = {
    case PhilosopherCreated(philosopher) =>
      watchWith(philosopher, BombExploded(philosopher)) // TODO: fix this. watching doesn't actually work
    case StartTheEvil =>
      scheduleBombThrowing(sender())
    case ThrowNewBomb(creator) =>
      if (!circulatingBomb) {
        bombThrowedAt = DateTime.now()
        println(s"💣 Devil is throwing a new bomb")
        creator ! Creator.Bomb
        circulatingBomb = true
      } else if (bombThrowedAt.plusSeconds(bombTimeout).getMillis < DateTime.now.getMillis) {
        println(s"💣 Devil's bomb probably got lost")
        circulatingBomb = false
      }
      scheduleBombThrowing(creator)
    case BombExploded(philosopher) =>
      println(s"💣 Devil got explosion confirmation in hands of ${philosopher.path.name}")
      circulatingBomb = false
  }

  private def scheduleBombThrowing(creator: ActorRef): Unit = {
    system.scheduler.scheduleOnce(2.seconds, self, ThrowNewBomb(creator))
  }
}

