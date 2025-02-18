// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.explorer

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util._

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.hotspot.DefaultExports
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.config._

object Main extends StrictLogging {
  def main(args: Array[String]): Unit = {
    try {
      (new BootUp).init()
    } catch {
      case error: Throwable =>
        logger.error(s"Cannot initialize system: $error")
    }
  }
}

@SuppressWarnings(Array("org.wartremover.warts.TryPartial"))
class BootUp extends StrictLogging {
  logger.info("Starting Explorer")
  logger.info(s"Build info: $BuildInfo")

  DefaultExports.initialize()

  private val typesafeConfig = ConfigFactory.load()

  implicit val config: ExplorerConfig = ExplorerConfig.load(typesafeConfig)

  implicit val databaseConfig: DatabaseConfig[PostgresProfile] =
    DatabaseConfig.forConfig[PostgresProfile]("db", typesafeConfig)

  implicit val actorSystem: ActorSystem           = ActorSystem()
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  private val explorer: ExplorerState = ExplorerState()

  def init(): Unit = {
    explorer
      .start()
      .onComplete {
        case Success(_) => ()
        case Failure(e) =>
          logger.error(s"Fatal error during initialization: $e")
          actorSystem.terminate()
      }

    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      sideEffect(Await.result(actorSystem.terminate(), 10.seconds))
    }))
  }

  CoordinatedShutdown(actorSystem).addTask(
    CoordinatedShutdown.PhaseBeforeActorSystemTerminate,
    "Shutdown services"
  ) { () =>
    for {
      _ <- explorer.stop()
    } yield Done
  }
}
