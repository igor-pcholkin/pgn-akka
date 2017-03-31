import java.net.URLEncoder

import akka.actor._
import akka.actor.Terminated


object JobReceptionist {
  def props = Props(new JobReceptionist)

  case object JobRequestStartGame

  case class JobParsePgnDone(workers: Set[ActorRef])
  case class JobAskMoveDone(move: String)

  abstract class Job(val name: String)
  case object JobParsePgn extends Job("parsePgn")
  case class JobAskMove(nextMove: String) extends Job("askMove")

  case object AskMove
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
      self ! AskMove

    case Terminated(jobMaster) =>
      log.error(s"Job Master ${jobMaster.path.name} terminated before finishing job.")

    case AskMove =>
      println("Move?")
      val move = scala.io.StdIn.readLine()
      askMoveMaster ! JobAskMove(move)

    case JobAskMoveDone(move) =>
      println(move)
      self ! AskMove
  }
}

trait CreateMasters {
  def context: ActorContext
  def createMasterParsePgn() = context.actorOf(JobMasterParsePgn.props, "master-parsePgn")
  def createMasterAskMove(receptionist: ActorRef, workers: Set[ActorRef]) = context.actorOf(JobMasterAskMove.props(receptionist, workers), "master-askMove")
}
