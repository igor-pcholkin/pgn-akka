
import scala.concurrent.duration._

import akka.actor._
import java.io.FileReader
import scala.concurrent.Future
import akka.pattern.pipe

object JobWorker {
  def props = Props(new JobWorker)

  case class Work(master: ActorRef)
  case class ParsePgnTask(pgnFiles: Seq[String], master: ActorRef)
  case class GetMove(prevMoves: Vector[String], master: ActorRef)
  case object ParsePgnDepleted
}

class JobWorker extends Actor
                   with ActorLogging {
  import JobMasterParsePgn._
  import JobMasterAskMove._
  import JobWorker._
  import context._

  var processed = 0
  var tree = Tree.create()

  def receive = idle

  def idle: Receive = {
    case Work(master) =>
      become(enlisted(master))

      log.info(s"Enlisted, will start requesting work for job '${master.path.name}'.")
      master ! Enlist(self)
      master ! NextTask

      setReceiveTimeout(30 seconds)
  }

  def enlisted(master: ActorRef): Receive = {
    case ReceiveTimeout =>
      master ! NextTask

    case ParsePgnTask(pgnFiles, master) =>
      log.info(s"Parsing $pgnFiles")

      pipe(processParsePgnTask(pgnFiles) map { result =>
      processed = processed + 1
      log.info(s"Processed $pgnFiles, asking for more work")
      master ! NextTask
      TaskResult(result)
    }) to master

    case ParsePgnDepleted =>
      log.info(s"Parsing of pgn done, idle")
      setReceiveTimeout(Duration.Undefined)

    case GetMove(prevMoves, master) =>
      log.info(s"Finding next move for moves: $prevMoves")
      val bestMove = tree.getMove(prevMoves)
      log.info(s"Found best move: $bestMove")
      master ! Move(bestMove)
  }

  def processParsePgnTask(fileNames: Seq[String]): Future[String] = Future {
    val games = fileNames.foldLeft(Seq[Game]()) { (allGames, fileName) =>
      println(s"Reading $fileName")
      PGNParser.parseAll(PGNParser.pgnfile, new FileReader(PgnFiles.baseDir + fileName + ".pgn")) match {
        case PGNParser.Success(games, _) => allGames ++ games
        case ex @ _                      => println(ex); allGames
      }
    }
    tree = games.foldLeft(tree) { (tree, game) =>
      tree.add(game)
    }

    "DONE"
  }
}
