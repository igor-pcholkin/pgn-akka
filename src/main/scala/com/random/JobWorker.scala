
package com.random

import scala.concurrent.duration._
import akka.actor._
import java.io.FileReader
import scala.concurrent.Future
import akka.pattern.pipe

object JobWorker {
  def props = Props(new JobWorker)

  case class Work(master: ActorRef)
  case class ParsePgnTask(pgnFiles: Seq[String])
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
  var readGames = 0

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

    case ParsePgnTask(pgnFiles) =>
      log.info(s"Parsing $pgnFiles, master: $master")

      pipe(processParsePgnTask(pgnFiles) map { result =>
      processed = processed + 1
      log.info(s"Processed $pgnFiles, asking for more work")
      master ! NextTask
      TaskResult(result, self)
    }) to master

    case ParsePgnDepleted =>
      log.info(s"Parsing of pgn done, idle")
      println(s"Added $readGames games")
      setReceiveTimeout(Duration.Undefined)

    case GetMove(prevMoves, master) =>
      log.info(s"Finding next move")
      val bestMove = tree.getMove(prevMoves)
      println(s"Found best move: $bestMove")
      master ! bestMove
  }

  def processParsePgnTask(fileNames: Seq[String]): Future[String] = Future {
    fileNames foreach { fileName =>
      println(s"Reading $fileName")
      val games = PGNParser.parseAll(PGNParser.pgnfile, new FileReader(PgnFiles.baseDir + fileName + ".pgn")) match {
        case PGNParser.Success(games, _) => games
        case ex @ _                      => println(ex); Nil
      }
      games foreach { game =>
        tree = tree.add(game, fileName)
      }
      readGames += games.length
    }

    "DONE"
  }
}
