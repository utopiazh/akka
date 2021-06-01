/*
 * Copyright (C) 2015-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.keyvaluestore

/**
 * A key-value store plugin must implement a class that implements this trait.
 * It provides the concrete implementations for the Java and Scala APIs.
 *
 * A key-value store plugin plugin must provide implementations for both
 * `akka.persistence.keyvaluestore.scaladsl.KeyValueStore` and `akka.persistence.keyvaluestore.javadsl.KeyValueStore`.
 * One of the implementations can delegate to the other.
 */
trait KeyValueStoreProvider {

  /**
   * The `ReadJournal` implementation for the Scala API.
   * This corresponds to the instance that is returned by [[KeyValueStoreRegistry#keyValueStoreFor]].
   */
  def scaladslKeyValueStore(): scaladsl.KeyValueStore[Any]

  /**
   * The `KeyValueStore` implementation for the Java API.
   * This corresponds to the instance that is returned by [[KeyValueStoreRegistry#getKeyValueStoreFor]].
   */
  def javadslKeyValueStore(): javadsl.KeyValueStore[AnyRef]
}
