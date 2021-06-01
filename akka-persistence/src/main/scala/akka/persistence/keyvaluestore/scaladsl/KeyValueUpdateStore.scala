/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.keyvaluestore.scaladsl

import scala.concurrent.Future

import akka.Done

/**
 * API for updating key-value objects.
 *
 * For Java API see [[akka.persistence.keyvaluestore.javadsl.KeyValueUpdateStore]].
 */
trait KeyValueUpdateStore[A] extends KeyValueStore[A] {

  /**
   * @param seqNr sequence number for optimistic locking. starts at 1.
   */
  def upsertObject(persistenceId: String, seqNr: Long, value: A, tag: String): Future[Done]

  def deleteObject(persistenceId: String): Future[Done]

}
