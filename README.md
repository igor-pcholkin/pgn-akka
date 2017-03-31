# pgn-akka

Plays chess using distributed DB implemented using akka clustering.

Application runs in 3 modes: seed, master and worker.
seed (>=1) - necessary only for clustering so that nodes can find each other
master (1) - user IO and interacts with workers, e.g. asking for the next move
worker (>=1) - creates a local DB, finds next move and recommends it to master

In order to start the app in each of 3 modes:
1. sbt assembly

seed:
java -DPORT=2551 -Dconfig.rource=/seed.conf -jar target/scala-2.11/pgn-akka.jar

master:
java -DPORT=2554 -Dconfig.resource=/master.conf -jar target/scala-2.11/pgn-akka.jar

worker:
java -DPORT=2555 -Dconfig.resource=/worker.conf -jar target/scala-2.11/pgn-akka.jar

Number of worker nodes should be choosen depending on number and size of pgn files to process.
So that each worker node could process about 50M of pgn files.
pgn files should be downloaded separately e.g. from http://www.pgnmentor.com/files.html#openings 
Directory and file names should be adjusted appropriately inside PgnFiles.scala.
