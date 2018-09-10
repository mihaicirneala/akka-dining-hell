package supervision.typed

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import org.joda.time.DateTime
import scala.concurrent.duration._


object Devil {
  sealed trait DevilProtocol
  final case class PhilosopherCreated(philosopher: ActorRef[Philosopher.PhilosopherProtocol]) extends DevilProtocol
  final case class StartTheEvil(creator: ActorRef[Creator.CreatorProtocol]) extends DevilProtocol
  final case class ThrowNewBomb(creator: ActorRef[Creator.CreatorProtocol]) extends DevilProtocol
  final case class BombExploded(philosopher: ActorRef[Philosopher.PhilosopherProtocol]) extends DevilProtocol

  val bombTimeout = 15
  val bombChances = 4

  val evil: Behavior[DevilProtocol] = Behaviors.setup(new Devil(_))

  def isBadLuck: Boolean = {
    val firstRandom = (new scala.util.Random).nextInt(bombChances)
    val secondRandom = (new scala.util.Random).nextInt(bombChances)
    firstRandom == secondRandom
  }
}

class Devil(ctx: ActorContext[Devil.DevilProtocol]) extends MutableBehavior[Devil.DevilProtocol] {
  import Devil._

  var circulatingBomb = false
  var bombThrowedAt = DateTime.now()

  override def onMessage(msg: Devil.DevilProtocol): Behavior[Devil.DevilProtocol] = {
    msg match {
      case PhilosopherCreated(philosopher) =>
        ctx.watchWith(philosopher, BombExploded(philosopher)) // TODO: fix this. watching doesn't actually work
        this
      case StartTheEvil(creator) =>
        scheduleBombThrowing(creator)
        this
      case ThrowNewBomb(creator) =>
        if (!circulatingBomb) {
          bombThrowedAt = DateTime.now()
          println(s"💣 Devil is throwing a new bomb")
          creator ! Creator.Bomb(ctx.self)
          circulatingBomb = true
        } else if (bombThrowedAt.plusSeconds(bombTimeout).getMillis < DateTime.now.getMillis) {
          println(s"💣 Devil's bomb probably got lost")
          circulatingBomb = false
        }
        scheduleBombThrowing(creator)
        this
      case BombExploded(philosopher) =>
        println(s"💣 Devil got explosion confirmation in hands of ${philosopher.path.name}")
        circulatingBomb = false
        this
      case _ => this
    }
  }

  private def scheduleBombThrowing(creator: ActorRef[Creator.CreatorProtocol]): Unit = {
    ctx.schedule(2.seconds, ctx.self, ThrowNewBomb(creator))
  }
}


