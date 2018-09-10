package dining.typed

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors


object Chopstick {
  import Philosopher._

  sealed trait ChopstickProtocol
  final case class PutChopstick(seat: TableSeat) extends ChopstickProtocol
  final case class TakeChopstick(seat: TableSeat) extends ChopstickProtocol

  val available: Behavior[ChopstickProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case TakeChopstick(seat) =>
        seat.philosopher ! ChopstickTaken(ctx.self, seat)
        Chopstick.taken
      case _ => Behaviors.same
    }
  }

  val taken: Behavior[ChopstickProtocol] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case TakeChopstick(otherSeat) =>
        otherSeat.philosopher ! ChopstickBusy(ctx.self, otherSeat)
        Behaviors.same
      case PutChopstick(seat) => Chopstick.available
      case _ => Behaviors.same
    }
  }
}
