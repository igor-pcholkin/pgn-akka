package com.random

import com.typesafe.config.ConfigFactory
import akka.actor.{Props, ActorSystem}
import akka.cluster.Cluster
import JobReceptionist.JobRequestStartGame
import akka.actor.actorRef2Scala
import akka.actor.ActorLogging

object Main extends App {
  val config = ConfigFactory.load()
  val system = ActorSystem("chess", config)

  println(s"Starting node with roles: ${Cluster(system).selfRoles}")

  if(system.settings.config.getStringList("akka.cluster.roles").contains("master")) {
    Cluster(system).registerOnMemberUp {
      val receptionist = system.actorOf(Props[JobReceptionist], "receptionist")
      println("Master node is ready.")

      receptionist ! JobRequestStartGame
      system.actorOf(Props(new ClusterDomainEventListener), "cluster-listener")
    }
  }
}
