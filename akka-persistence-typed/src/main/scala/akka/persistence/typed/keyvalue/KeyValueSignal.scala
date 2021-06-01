/*
 * Copyright (C) 2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.keyvalue

import akka.actor.typed.Signal
import akka.annotation.DoNotInherit

/**
 * Supertype for all `KeyValueBehavior` specific signals
 *
 * Not for user extension
 */
@DoNotInherit
sealed trait KeyValueSignal extends Signal

@DoNotInherit sealed abstract class RecoveryCompleted extends KeyValueSignal
case object RecoveryCompleted extends RecoveryCompleted {
  def instance: RecoveryCompleted = this
}

final case class RecoveryFailed(failure: Throwable) extends KeyValueSignal {

  /**
   * Java API
   */
  def getFailure(): Throwable = failure
}
