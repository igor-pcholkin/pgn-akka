

package com.random

import scala.concurrent.duration._
import akka.actor._
import akka.cluster.routing._
import akka.routing._
import scala.util.Random
import scala.Vector

object JobMasterParsePgn {
  def props = Props(new JobMasterParsePgn)

  case class Enlist(worker: ActorRef)

  case object NextTask
  case class TaskResult(result: String, sender: ActorRef)

}

class JobMasterParsePgn extends Actor
                   with ActorLogging
                   with CreateWorkerRouter {
  import JobReceptionist._
  import JobMasterParsePgn._
  import JobWorker._
  import context._

  var pgnFilesWorkParts = List[String]()
  var intermediateResult = Vector[String]()
  var workGiven = 0
  var workReceived = 0
  var workers = Set[ActorRef]()

  val router = createWorkerRouter

  override def supervisorStrategy: SupervisorStrategy =
    SupervisorStrategy.stoppingStrategy

  def receive = idle

  def idle: Receive = {
    case JobParsePgn =>
      pgnFilesWorkParts = Random.shuffle(PgnFiles.fileNames.toList)
      val cancellable = context.system.scheduler.schedule(0 millis, 1000 millis, router, Work(self))
      context.setReceiveTimeout(10 seconds)
      become(working(sender, cancellable))
  }

  def working(receptionist: ActorRef,
              cancellable: Cancellable): Receive = {
    case Enlist(worker) =>
      watch(worker)

    case NextTask =>
      if (pgnFilesWorkParts.isEmpty) {
        sender() ! ParsePgnDepleted
      } else {
        sender() ! ParsePgnTask(Seq(pgnFilesWorkParts.head))
        workGiven = workGiven + 1
        pgnFilesWorkParts = pgnFilesWorkParts.tail
      }

    case TaskResult(result, worker) =>
      intermediateResult = intermediateResult :+ result
      workReceived = workReceived + 1
      workers = workers + worker
      log.info(s"Received parsing result from ${worker}")

      if (pgnFilesWorkParts.isEmpty && workGiven == workReceived) {
        completeParsing(receptionist, cancellable)
      }

    case ReceiveTimeout =>
      if (workers.isEmpty) {
        log.info(s"No workers responded in time. Cancelling parse pgn job.")
        stop(self)
      } else {
        completeParsing(receptionist, cancellable)
      }

  }

  def completeParsing(receptionist: ActorRef,
              cancellable: Cancellable) {
    cancellable.cancel()
    setReceiveTimeout(Duration.Undefined)
    receptionist ! JobParsePgnDone(workers)
    log.info(s"Parsing complete.")
  }

}


trait CreateWorkerRouter { this: Actor =>
  def createWorkerRouter: ActorRef = {
    context.actorOf(
      ClusterRouterPool(BroadcastPool(20), ClusterRouterPoolSettings(
        totalInstances = 20, maxInstancesPerNode = 5,
        allowLocalRoutees = false, useRole = Some("worker"))).props(Props[JobWorker]),
      name = "worker-router")
  }
}
