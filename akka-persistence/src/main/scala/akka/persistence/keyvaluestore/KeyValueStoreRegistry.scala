/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.keyvaluestore

import scala.reflect.ClassTag

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.annotation.InternalApi
import akka.persistence.PersistencePlugin
import akka.persistence.PluginProvider
import akka.persistence.keyvaluestore.scaladsl.KeyValueStore
import akka.util.unused

/**
 * Persistence extension for queries.
 */
object KeyValueStoreRegistry extends ExtensionId[KeyValueStoreRegistry] with ExtensionIdProvider {

  override def get(system: ActorSystem): KeyValueStoreRegistry = super.get(system)
  override def get(system: ClassicActorSystemProvider): KeyValueStoreRegistry = super.get(system)

  def createExtension(system: ExtendedActorSystem): KeyValueStoreRegistry = new KeyValueStoreRegistry(system)

  def lookup: KeyValueStoreRegistry.type = KeyValueStoreRegistry

  @InternalApi
  private[akka] val pluginProvider: PluginProvider[KeyValueStoreProvider, KeyValueStore[_], javadsl.KeyValueStore[_]] =
    new PluginProvider[KeyValueStoreProvider, scaladsl.KeyValueStore[_], javadsl.KeyValueStore[_]] {
      override def scalaDsl(t: KeyValueStoreProvider): KeyValueStore[_] = t.scaladslKeyValueStore()
      override def javaDsl(t: KeyValueStoreProvider): javadsl.KeyValueStore[_] = t.javadslKeyValueStore()
    }

}

class KeyValueStoreRegistry(system: ExtendedActorSystem)
    extends PersistencePlugin[scaladsl.KeyValueStore[_], javadsl.KeyValueStore[_], KeyValueStoreProvider](system)(
      ClassTag(classOf[KeyValueStoreProvider]),
      KeyValueStoreRegistry.pluginProvider)
    with Extension {

  /**
   * Scala API: Returns the [[akka.persistence.keyvaluestore.scaladsl.KeyValueStore]] specified by the given
   * read journal configuration entry.
   *
   * The provided keyValueStorePluginConfig will be used to configure the journal plugin instead of the actor system
   * config.
   */
  final def KeyValueStoreFor[T <: scaladsl.KeyValueStore[_]](
      keyValueStorePluginId: String,
      keyValueStorePluginConfig: Config): T =
    pluginFor(keyValueStorePluginId, keyValueStorePluginConfig).scaladslPlugin.asInstanceOf[T]

  /**
   * Scala API: Returns the [[akka.persistence.keyvaluestore.scaladsl.KeyValueStore]] specified by the given
   * read journal configuration entry.
   */
  final def keyValueStoreFor[T <: scaladsl.KeyValueStore[_]](keyValueStorePluginId: String): T =
    KeyValueStoreFor(keyValueStorePluginId, ConfigFactory.empty)

  /**
   * Java API: Returns the [[akka.persistence.keyvaluestore.javadsl.KeyValueStore]] specified by the given
   * read journal configuration entry.
   */
  final def getKeyValueStoreFor[T <: javadsl.KeyValueStore[_]](
      @unused clazz: Class[T], // FIXME generic Class could be problematic in Java
      keyValueStorePluginId: String,
      keyValueStorePluginConfig: Config): T =
    pluginFor(keyValueStorePluginId, keyValueStorePluginConfig).javadslPlugin.asInstanceOf[T]

  final def getKeyValueStoreFor[T <: javadsl.KeyValueStore[_]](clazz: Class[T], KeyValueStorePluginId: String): T =
    getKeyValueStoreFor[T](clazz, KeyValueStorePluginId, ConfigFactory.empty())

}
