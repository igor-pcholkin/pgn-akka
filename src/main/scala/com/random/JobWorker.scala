
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
      pipe (
        Future {
          log.info(s"Finding next move")
          val bestMoveForCurrentRoute = tree.getMove(prevMoves)
          val bestMove = if (bestMoveForCurrentRoute.move == "DRAW?" && prevMoves.length <= 12) {
            val altMove = getMoveForAltGameRoutes(prevMoves)
            if (altMove.move != "DRAW?") {
              println(s"Found alternative route: ${altMove.gameRoute}")
            }
            altMove
          } else {
            bestMoveForCurrentRoute
          }
          if (bestMove.move != "DRAW?") {
            println(s"Found best move: $bestMove")
          }
          bestMove
      }) to master
  }

  def getMoveForAltGameRoutes(prevMoves: Vector[String]) = {

    val whites = (prevMoves.zipWithIndex collect { case (move, i) if i % 2 == 0 => move
    }).permutations.toStream

    val blacks = (prevMoves.zipWithIndex collect { case (move, i) if i % 2 == 1 => move
    }).permutations.toStream

    val altGames = for {
      whiteMoves <- whites
      blackMoves <- blacks
    } yield (whiteMoves zipAll (blackMoves, None, None) map { pair =>
      List(pair._1, pair._2)
    } flatten) collect { case s: String => s }

    val (altMove, altRoute) = altGames map { altGameRoute =>
      (tree.getMove(altGameRoute), altGameRoute)
    } find { case (move, route) => move.move != "DRAW?"} getOrElse(Move("DRAW?"), prevMoves)
    altMove.copy(gameRoute = Some(altRoute))
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
