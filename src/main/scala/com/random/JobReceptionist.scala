package com.random

import akka.actor._
import akka.actor.Terminated

object JobReceptionist {
  def props = Props(new JobReceptionist)

  case object JobRequestStartGame

  case class JobParsePgnDone(workers: Set[ActorRef])
  case class JobAskMoveDone(move: String)

  abstract class Job(val name: String)
  case object JobParsePgn extends Job("parsePgn")
  case class JobAskMove(nextMove: Option[String], newGame: Boolean) extends Job("askMove")

  case class AskMove(newGame: Boolean)
  case object StartNewGame
}

class JobReceptionist extends Actor
                         with ActorLogging
                         with CreateMasters {
  import JobReceptionist._
  import JobMasterAskMove._
  import context._

  override def supervisorStrategy: SupervisorStrategy =
    SupervisorStrategy.stoppingStrategy

  var parsePgnMaster: ActorRef = _
  var askMoveMaster: ActorRef = _

  def receive = {
    case jr @ JobRequestStartGame =>
      log.info(s"Received job request to start game")

      parsePgnMaster = createMasterParsePgn()
      val jobParsePgn = JobParsePgn

      parsePgnMaster ! jobParsePgn
      watch(parsePgnMaster)

    case JobParsePgnDone(workers) =>
      log.info(s"Parse pgn Job complete, starting game")
      askMoveMaster = createMasterAskMove(self, workers)
      self ! StartNewGame

    case Terminated(jobMaster) =>
      log.error(s"Job Master ${jobMaster.path.name} terminated before finishing job.")

    case StartNewGame =>
      println("New Game. (W/B)?")
      val wb = scala.io.StdIn.readLine()
      if (wb.toUpperCase == "W") {
        self ! AskMove(true)
      } else {
        askMoveMaster ! JobAskMove(None, true)
      }

    case AskMove(newGame) =>
      println("Move?")
      val move = scala.io.StdIn.readLine()
      askMoveMaster ! JobAskMove(Some(move), newGame)

    case JobAskMoveDone(move) =>
      println(move)
      if (move == "DRAW?") {
        self ! StartNewGame
      } else {
        self ! AskMove(false)
      }


  }
}

trait CreateMasters {
  def context: ActorContext
  def createMasterParsePgn() = context.actorOf(JobMasterParsePgn.props, "master-parsePgn")
  def createMasterAskMove(receptionist: ActorRef, workers: Set[ActorRef]) = context.actorOf(JobMasterAskMove.props(receptionist, workers), "master-askMove")
}
