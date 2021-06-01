/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.keyvalue.internal

import akka.annotation.InternalApi
import akka.persistence.typed.PersistenceId

/**
 * INTERNAL API
 *
 * Used for store failures. Private to akka as only internal supervision strategies should use it.
 */
@InternalApi
final private[akka] class KeyValueStoreException(msg: String, cause: Throwable) extends RuntimeException(msg, cause) {
  def this(persistenceId: PersistenceId, sequenceNr: Long, cause: Throwable) =
    this(s"Failed to persist state with sequence number [$sequenceNr] for persistenceId [${persistenceId.id}]", cause)
}
