

package com.random

import scala.concurrent.duration._
import akka.actor._
import akka.cluster.routing._
import akka.routing._
import scala.Vector
import scala.util.Random

object JobMasterAskMove {
  def props(receptionist: ActorRef, workers: Set[ActorRef]) = Props(classOf[JobMasterAskMove], receptionist, workers)

}

class JobMasterAskMove(receptionist: ActorRef, val workers: Set[ActorRef]) extends Actor
                   with ActorLogging {
  import com.random.JobReceptionist._
  import com.random.JobMasterAskMove._
  import com.random.JobWorker._
  import context._

  var moves: Vector[String] = Vector()
  var workReceived = 0
  var nextMoveCandidates = Set[Move]()

  override def supervisorStrategy: SupervisorStrategy =
    SupervisorStrategy.stoppingStrategy

  def receive = {

    case JobAskMove(mayBeNextMove, newGame) =>
      if (newGame) {
        moves = Vector()
      }
      mayBeNextMove match {
        case Some(nextMove) => moves = moves :+ nextMove
        case None => //
      }
      log.info(s"moves: $moves")
      context.setReceiveTimeout(60 seconds)
      workReceived = 0
      nextMoveCandidates = Set.empty
      workers foreach { worker =>
        worker ! GetMove(moves, self)
      }

    case m@Move(move, meta, rank, mayBeGameRoute) =>
      log.info(s"Received move candidate: $move, $meta")
      if (move != "DRAW?") {
        nextMoveCandidates = nextMoveCandidates + m
      }
      workReceived = workReceived + 1

      if (workReceived == workers.size) {
        setReceiveTimeout(Duration.Undefined)
        val mayBeBestMove = if (moves.length <= 4) {
          Random.shuffle(nextMoveCandidates.toSeq).headOption
        } else {
          nextMoveCandidates.toSeq.sortBy(_.rank).lastOption
        }
        val bestMove = mayBeBestMove.getOrElse(Move("DRAW?"))
        log.info(s"Choosing move: $bestMove")
        bestMove.gameRoute match {
          case Some(route) => moves = route
          case None =>        moves = moves :+ bestMove.move
        }

        receptionist ! JobAskMoveDone(bestMove.move)
      }

    case ReceiveTimeout =>
      if (workers.isEmpty) {
        log.info(s"No workers responded in time. Cancelling ask move job.")
        stop(self)
      } else setReceiveTimeout(Duration.Undefined)

  }

}

