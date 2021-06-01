/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.keyvaluestore.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage

/**
 * API for reading key-value objects.
 *
 * For Scala API see [[akka.persistence.keyvaluestore.scaladsl.KeyValueStore]].
 *
 * See also [[KeyValueUpdateStore]]
 */
trait KeyValueStore[A] {

  def getObject(persistenceId: String): CompletionStage[GetObjectResult[A]]

}

final case class GetObjectResult[A](value: Optional[A], seqNr: Long)
