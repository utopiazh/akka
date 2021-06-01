/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.keyvalue.internal

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

import com.typesafe.config.Config

import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.persistence.Persistence

/**
 * INTERNAL API
 */
@InternalApi private[akka] object KeyValueSettings {

  def apply(system: ActorSystem[_], keyValueStorePluginId: String): KeyValueSettings =
    apply(system.settings.config, keyValueStorePluginId)

  def apply(config: Config, keyValueStorePluginId: String): KeyValueSettings = {
    val typedConfig = config.getConfig("akka.persistence.typed")

    val stashOverflowStrategy = typedConfig.getString("stash-overflow-strategy").toLowerCase match {
      case "drop" => StashOverflowStrategy.Drop
      case "fail" => StashOverflowStrategy.Fail
      case unknown =>
        throw new IllegalArgumentException(s"Unknown value for stash-overflow-strategy: [$unknown]")
    }

    val stashCapacity = typedConfig.getInt("stash-capacity")
    require(stashCapacity > 0, "stash-capacity MUST be > 0, unbounded buffering is not supported.")

    val logOnStashing = typedConfig.getBoolean("log-stashing")

    val keyValueStoreConfig = keyValueStoreConfigFor(config, keyValueStorePluginId)
    val recoveryTimeout: FiniteDuration =
      keyValueStoreConfig.getDuration("recovery-timeout", TimeUnit.MILLISECONDS).millis

    val useContextLoggerForInternalLogging = typedConfig.getBoolean("use-context-logger-for-internal-logging")

    KeyValueSettings(
      stashCapacity = stashCapacity,
      stashOverflowStrategy,
      logOnStashing = logOnStashing,
      recoveryTimeout,
      keyValueStorePluginId,
      useContextLoggerForInternalLogging)
  }

  private def keyValueStoreConfigFor(config: Config, pluginId: String): Config = {
    def defaultPluginId = {
      val configPath = config.getString("akka.persistence.key-value.plugin")
      Persistence.verifyPluginConfigIsDefined(configPath, "Default KeyValueStore")
      configPath
    }

    val configPath = if (pluginId == "") defaultPluginId else pluginId
    Persistence.verifyPluginConfigExists(config, configPath, "KeyValueStore")
    config.getConfig(configPath)
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final case class KeyValueSettings(
    stashCapacity: Int,
    stashOverflowStrategy: StashOverflowStrategy,
    logOnStashing: Boolean,
    recoveryTimeout: FiniteDuration,
    keyValueStorePluginId: String,
    useContextLoggerForInternalLogging: Boolean) {

  require(
    keyValueStorePluginId != null,
    "key-value store plugin id must not be null; use empty string for 'default' key-value store")
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] sealed trait StashOverflowStrategy

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object StashOverflowStrategy {
  case object Drop extends StashOverflowStrategy
  case object Fail extends StashOverflowStrategy
}
