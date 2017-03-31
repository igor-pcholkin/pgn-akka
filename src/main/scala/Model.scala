object Result extends Enumeration {
  type Result = Value
  val WWIN, BWIN, DRAW = Value
}
import Result._
import scala.util.Try
import scala.util.Failure
import scala.util.Success

case class Meta(result: Result, eloWhite: Int, eloBlack:Int)
case class Game(meta: Meta, moves: List[String])

object Meta {
  def create(header: Map[String, String]) = {
    val result = header.getOrElse("Result", "1/2-1/2") match {
      case "1-0" => WWIN
      case "0-1" => BWIN
      case _ => DRAW
    }
    val eloWhite = getElo(header, "WhiteElo")
    val eloBlack = getElo(header, "BlackElo")
    Meta(result, eloWhite, eloBlack)
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


