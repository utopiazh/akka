/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.keyvalue.javadsl

import java.util.Optional

import akka.actor.typed
import akka.actor.typed.BackoffSupervisorStrategy
import akka.actor.typed.Behavior
import akka.actor.typed.internal.BehaviorImpl.DeferredBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.annotation.InternalApi
import akka.persistence.typed.keyvalue.internal
import akka.persistence.typed.keyvalue.internal._
import akka.persistence.typed.keyvalue.scaladsl
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.SnapshotAdapter

/**
 * A `Behavior` for a persistent actor with key-value storage of its state.
 */
abstract class KeyValueBehavior[Command, State] private[akka] (
    val persistenceId: PersistenceId,
    onPersistFailure: Optional[BackoffSupervisorStrategy])
    extends DeferredBehavior[Command] {

  /**
   * @param persistenceId stable unique identifier for the `KeyValueBehavior`
   */
  def this(persistenceId: PersistenceId) = {
    this(persistenceId, Optional.empty[BackoffSupervisorStrategy])
  }

  /**
   * If using onPersistFailure the supervision is only around the `KeyValueBehavior` not any outer setup/withTimers
   * block. If using restart any actions e.g. scheduling timers, can be done on the PreRestart signal or on the
   * RecoveryCompleted signal.
   *
   * @param persistenceId stable unique identifier for the `KeyValueBehavior`
   * @param onPersistFailure BackoffSupervisionStrategy for persist failures
   */
  def this(persistenceId: PersistenceId, onPersistFailure: BackoffSupervisorStrategy) = {
    this(persistenceId, Optional.ofNullable(onPersistFailure))
  }

  /**
   * Factory of effects.
   *
   * Return effects from your handlers in order to instruct persistence on how to act on the incoming message (i.e. persist state).
   */
  protected final def Effect: EffectFactories[State] =
    EffectFactories.asInstanceOf[EffectFactories[State]]

  /**
   * Implement by returning the initial empty state object.
   * This object will be passed into this behaviors handlers, until a new state replaces it.
   *
   * Also known as "zero state" or "neutral state".
   */
  protected def emptyState: State

  /**
   * Implement by handling incoming commands and return an `Effect()` to persist or signal other effects
   * of the command handling such as stopping the behavior or others.
   *
   * Use [[KeyValueBehavior#newCommandHandlerBuilder]] to define the command handlers.
   *
   * The command handlers are only invoked when the actor is running (i.e. not recovering).
   * While the actor is persisting state, the incoming messages are stashed and only
   * delivered to the handler once persisting them has completed.
   */
  protected def commandHandler(): CommandHandler[Command, State]

  /**
   * Override to react on general lifecycle signals and `KeyValueBehavior` specific signals
   * (recovery related). Those are all subtypes of [[akka.persistence.typed.keyvalue.KeyValueSignal]].
   *
   * Use [[KeyValueBehavior#newSignalHandlerBuilder]] to define the signal handler.
   */
  protected def signalHandler(): SignalHandler[State] = SignalHandler.empty[State]

  /**
   * @return A new, mutable signal handler builder
   */
  protected final def newSignalHandlerBuilder(): SignalHandlerBuilder[State] =
    SignalHandlerBuilder.builder[State]

  /**
   * @return A new, mutable, command handler builder
   */
  protected def newCommandHandlerBuilder(): CommandHandlerBuilder[Command, State] = {
    CommandHandlerBuilder.builder[Command, State]()
  }

  /**
   * Override and define the `KeyValueStore` plugin id that this actor should use instead of the default.
   */
  def keyValueStorePluginId: String = ""

  /**
   * The tag that can be used in persistence query.
   */
  def tag: String = ""

  /**
   * Transform the state into another type before giving it to and from the store. Can be used
   * to migrate from different state types e.g. when migration from PersistentFSM to Typed KeyValueBehavior.
   */
  def snapshotAdapter(): SnapshotAdapter[State] = NoOpSnapshotAdapter.instance[State]

  /**
   * INTERNAL API: DeferredBehavior init, not for user extension
   */
  @InternalApi override def apply(context: typed.TypedActorContext[Command]): Behavior[Command] =
    createKeyValueBehavior()

  /**
   * INTERNAL API
   */
  @InternalApi private[akka] final def createKeyValueBehavior(): scaladsl.KeyValueBehavior[Command, State] = {

    val behavior = new internal.KeyValueBehaviorImpl[Command, State](
      persistenceId,
      emptyState,
      (state, cmd) => commandHandler()(state, cmd).asInstanceOf[EffectImpl[State]],
      getClass).withTag(tag).snapshotAdapter(snapshotAdapter()).withKeyValueStorePluginId(keyValueStorePluginId)

    val handler = signalHandler()
    val behaviorWithSignalHandler =
      if (handler.isEmpty) behavior
      else behavior.receiveSignal(handler.handler)

    if (onPersistFailure.isPresent)
      behaviorWithSignalHandler.onPersistFailure(onPersistFailure.get)
    else
      behaviorWithSignalHandler
  }

  /**
   * The last sequence number that was persisted, can only be called from inside the handlers of a `KeyValueBehavior`
   */
  final def lastSequenceNumber(ctx: ActorContext[_]): Long = {
    scaladsl.KeyValueBehavior.lastSequenceNumber(ctx.asScala)
  }

}

/**
 * A [[KeyValueBehavior]] that is enforcing that replies to commands are not forgotten.
 * There will be compilation errors if the returned effect isn't a [[ReplyEffect]], which can be
 * created with `Effects().reply`, `Effects().noReply`, [[EffectBuilder.thenReply]], or [[EffectBuilder.thenNoReply]].
 */
abstract class KeyValueBehaviorWithEnforcedReplies[Command, State](
    persistenceId: PersistenceId,
    backoffSupervisorStrategy: Optional[BackoffSupervisorStrategy])
    extends KeyValueBehavior[Command, State](persistenceId, backoffSupervisorStrategy) {

  def this(persistenceId: PersistenceId) = {
    this(persistenceId, Optional.empty[BackoffSupervisorStrategy])
  }

  def this(persistenceId: PersistenceId, backoffSupervisorStrategy: BackoffSupervisorStrategy) = {
    this(persistenceId, Optional.ofNullable(backoffSupervisorStrategy))
  }

  /**
   * Implement by handling incoming commands and return an `Effect()` to persist or signal other effects
   * of the command handling such as stopping the behavior or others.
   *
   * Use [[KeyValueBehaviorWithEnforcedReplies#newCommandHandlerWithReplyBuilder]] to define the command handlers.
   *
   * The command handlers are only invoked when the actor is running (i.e. not recovering).
   * While the actor is persisting state, the incoming messages are stashed and only
   * delivered to the handler once persisting them has completed.
   */
  override protected def commandHandler(): CommandHandlerWithReply[Command, State]

  /**
   * @return A new, mutable, command handler builder
   */
  protected def newCommandHandlerWithReplyBuilder(): CommandHandlerWithReplyBuilder[Command, State] = {
    CommandHandlerWithReplyBuilder.builder[Command, State]()
  }

  /**
   * Use [[KeyValueBehaviorWithEnforcedReplies#newCommandHandlerWithReplyBuilder]] instead, or
   * extend [[KeyValueBehavior]] instead of [[KeyValueBehaviorWithEnforcedReplies]].
   *
   * @throws UnsupportedOperationException use newCommandHandlerWithReplyBuilder instead
   */
  override protected def newCommandHandlerBuilder(): CommandHandlerBuilder[Command, State] =
    throw new UnsupportedOperationException("Use newCommandHandlerWithReplyBuilder instead")

}
