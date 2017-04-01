package com.random

class Tree(rootNode: Node) {
  def add(game: Game, chessBaseName: String): Tree = {
    val meta = game.meta.copy(chessBase = Some(chessBaseName))
    new Tree(rootNode.add(game.moves, meta))
  }
  def print = rootNode.print
  def getMove(prevMoves: Vector[String]): Move = rootNode.getMove(prevMoves)
}

object Tree {
  def create() = {
    val rootNode = new Node(None, None, Nil, 0)
    new Tree(rootNode)
  }
  def createSubTree(moves: List[String], meta: Meta): List[Node] = {
    moves match {
      case h :: rest =>
        val subtree = createSubTree(rest, meta)
        List(new Node(Some(h), Some(meta), subtree, nodecount(subtree)))
      case Nil => Nil
    }
  }
  def print(tree: Tree) = {
    tree.print
  }
  def nodecount(nodes: Seq[Node]) = nodes.foldLeft(1) { (sum, node) => sum + node.numSubNodes }
}

class Node(val move: Option[String], val meta: Option[Meta], val subtrees: List[Node], val numSubNodes: Int) {
  def print: Unit = print(1, this)

  def print(level: Int, node: Node): Unit = {
    val sps = " " * level
    val sps1 = " " * (level + 1)
    println(s"$sps{")
    println(s"""$sps1${node.move.getOrElse("")}""")
    println(s"$sps1[")
    subtrees.foreach { subn =>
      subn.print(level + 2, subn)
      println(s"$sps1,")
    }
    println(s"$sps1]")
    println(s"$sps}")
  }

  def add(newMoves: List[String], newMeta: Meta): Node = {
    newMoves match {
      case firstNewMove :: restOfNewMoves =>
        val (foundMoves, restOfExistingMoves) = subtrees.partition { node => node.move == Some(firstNewMove) }
        val newSubtrees = (foundMoves.headOption match {
          case Some(foundMoveNode) => foundMoveNode.add(restOfNewMoves, newMeta)
          case None                => Tree.createSubTree(newMoves, newMeta).head
        }) :: restOfExistingMoves
        new Node(move, meta, newSubtrees, Tree.nodecount(newSubtrees))
      case Nil => this
    }
  }

  def getMove(prevMoves: Vector[String]): Move = {
    val drawMove = Move("DRAW?")
    if (prevMoves.length > 0) {
      val move = prevMoves(0)
      val rest = prevMoves.tail
      subtrees.find(node => node.move == Some(move)) match {
        case Some(foundMoveNode) =>
          foundMoveNode.getMove(rest)
        case _ => drawMove
      }
    } else {
      val candidates = subtrees.sortWith((node1, node2) => node1.numSubNodes > node2.numSubNodes)
      println("Candidates: " + (candidates.map { node =>
        node.move + " (" + node.numSubNodes.toString + " )"
      }).mkString(","))
      val mayBeBestMoveNode = candidates.headOption
      println("Next Expected moves: " + (mayBeBestMoveNode.map(_.subtrees).getOrElse(Nil).map { node =>
        node.move + " (" + node.numSubNodes.toString + " )"
      }).mkString(","))
      (mayBeBestMoveNode.map { bestMoveNode =>
       Move(bestMoveNode.move.getOrElse(""), bestMoveNode.meta)
      }).getOrElse(drawMove)
    }
  }
}
