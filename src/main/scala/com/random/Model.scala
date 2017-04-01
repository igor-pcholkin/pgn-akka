package com.random

import scala.util.Try
import scala.util.Failure
import scala.util.Success

object Result extends Enumeration {
  type Result = Value
  val WWIN, BWIN, DRAW = Value
}

import Result._

case class Meta(result: Result, white: String, black: String, eloWhite: Int, eloBlack:Int, chessBase: Option[String] = None)
case class Game(meta: Meta, moves: List[String])
case class Move(move: String, meta: Option[Meta] = None)


object Meta {
  def create(header: Map[String, String]) = {
    val result = header.getOrElse("Result", "1/2-1/2") match {
      case "1-0" => WWIN
      case "0-1" => BWIN
      case _ => DRAW
    }
    val eloWhite = getElo(header, "WhiteElo")
    val eloBlack = getElo(header, "BlackElo")
    val white = header.getOrElse("White", "")
    val black = header.getOrElse("Black", "")
    Meta(result, white, black, eloWhite, eloBlack)
  }

  def getElo(header: Map[String, String], key: String) = {
    Try {
      header.getOrElse("WhiteElo", "2000").toInt
    } match {
      case Success(value) => value
      case Failure(ex) => 2000
    }
  }
}


