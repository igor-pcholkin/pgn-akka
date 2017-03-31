

import scala.concurrent.duration._

import akka.actor._
import akka.cluster.routing._
import akka.routing._


object JobMasterAskMove {
  def props(receptionist: ActorRef, workers: Set[ActorRef]) = Props(classOf[JobMasterAskMove], receptionist, workers)

  case class Move(move: String)

}

class JobMasterAskMove(receptionist: ActorRef, val workers: Set[ActorRef]) extends Actor
                   with ActorLogging {
  import JobReceptionist._
  import JobMasterAskMove._
  import JobWorker._
  import context._

  var moves: Vector[String] = Vector()
  var workReceived = 0
  var nextMoveCandidates = Set[String]()

  override def supervisorStrategy: SupervisorStrategy =
    SupervisorStrategy.stoppingStrategy

  def receive = {

    case JobAskMove(nextMove) =>
      log.info(s"new move: $nextMove")
      moves = moves :+ nextMove
      log.info(s"moves: $moves")
      context.setReceiveTimeout(60 seconds)
      workReceived = 0
      nextMoveCandidates = Set.empty
      workers foreach { worker =>
        worker ! GetMove(moves, self)
      }
    case Move(move) =>
      log.info(s"Received move candidate: $move")
      if (move != "DRAW?") {
        nextMoveCandidates = nextMoveCandidates + move
      }
      workReceived = workReceived + 1

      if (workReceived == workers.size) {
        setReceiveTimeout(Duration.Undefined)
        val bestMove = nextMoveCandidates.headOption.getOrElse("DRAW?")
        log.info(s"Choosing move: $bestMove")
        moves = moves :+ bestMove
        receptionist ! JobAskMoveDone(bestMove)
      }

    case ReceiveTimeout =>
      if (workers.isEmpty) {
        log.info(s"No workers responded in time. Cancelling ask move job.")
        stop(self)
      } else setReceiveTimeout(Duration.Undefined)

  }

}

