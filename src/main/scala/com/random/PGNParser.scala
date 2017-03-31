package com.random

import scala.util.parsing.combinator.JavaTokenParsers

object PGNParser extends JavaTokenParsers {
  def pgnfile = rep1(game)

  def game = (header <~ comments.?) ~ moves.? ~ "*".? ^^ { case header ~ moves ~ ast => Game(Meta.create(header), moves.getOrElse(Nil)) }

  def header = rep1(headerLine) ^^ { case keyValueList => keyValueList.toMap }

  def headerLine = "[" ~> (headerName ~ headerValue) <~ "]" ^^ { case name ~ value => name -> value }

  def headerName = ident

  def headerValue = "\"[^\"]*\"".r ^^ { case s => s.substring(1, s.length - 1) }

  def moves = rep(moveSpec) <~ result ^^ { moves => moves.flatten }

  def moveSpec: PGNParser.Parser[Seq[String]] = normalMove | ignoredMoves

  def normalMove = moveNo ~> (white <~ moneyWin.?) ~ (black.? <~ moneyWin.?) <~ comments.? ^^ { case w ~ b =>
    Seq(w) ++ b.map(Seq(_)).getOrElse(Nil)
  }

  def ignoredMoves = "(" ~ rep1(moveSpec) ~ ")" ^^ { case _ => Nil }

  def moveNo = wholeNumber <~ "."

  def white = move

  def black = move

  def move = """[a-zA-z][a-zA-z1-8-+=#]+""".r | ".."

  def result = comments.? ~> ("1-0" | "0-1" | ("1/2" ~ "-".? ~ "1/2") | "*")

  def moneyWin = "$" ~ wholeNumber

  def comments = "{" ~ """[^}]*""".r ~ "}"
}
