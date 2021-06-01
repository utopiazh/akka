/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.keyvaluestore.scaladsl

import scala.concurrent.Future

/**
 * API for reading key-value objects.
 *
 * For Java API see [[akka.persistence.keyvaluestore.javadsl.KeyValueStore]].
 *
 * See also [[KeyValueUpdateStore]]
 */
trait KeyValueStore[A] {

  def getObject(persistenceId: String): Future[GetObjectResult[A]]

}

final case class GetObjectResult[A](value: Option[A], seqNr: Long)
