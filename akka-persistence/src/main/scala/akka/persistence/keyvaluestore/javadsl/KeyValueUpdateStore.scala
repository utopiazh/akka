/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.keyvaluestore.javadsl

import java.util.concurrent.CompletionStage

import akka.Done

/**
 * API for updating key-value objects.
 *
 * For Scala API see [[akka.persistence.keyvaluestore.scaladsl.KeyValueUpdateStore]].
 */
trait KeyValueUpdateStore[A] extends KeyValueStore[A] {

  /**
   * @param seqNr sequence number for optimistic locking. starts at 1.
   */
  def upsertObject(persistenceId: String, seqNr: Long, value: A, tag: String): CompletionStage[Done]

  def deleteObject(persistenceId: String): CompletionStage[Done]
}
