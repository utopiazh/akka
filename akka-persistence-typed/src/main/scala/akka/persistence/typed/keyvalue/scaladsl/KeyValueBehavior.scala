/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.keyvalue.scaladsl

import scala.annotation.tailrec

import akka.actor.typed.BackoffSupervisorStrategy
import akka.actor.typed.Behavior
import akka.actor.typed.Signal
import akka.actor.typed.internal.BehaviorImpl.DeferredBehavior
import akka.actor.typed.internal.InterceptorImpl
import akka.actor.typed.internal.LoggerClass
import akka.actor.typed.scaladsl.ActorContext
import akka.annotation.DoNotInherit
import akka.persistence.typed.keyvalue.internal._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.SnapshotAdapter

object KeyValueBehavior {

  /**
   * Type alias for the command handler function that defines how to act on commands.
   *
   * The type alias is not used in API signatures because it's easier to see (in IDE) what is needed
   * when full function type is used. When defining the handler as a separate function value it can
   * be useful to use the alias for shorter type signature.
   */
  type CommandHandler[Command, State] = (State, Command) => Effect[State]

  private val logPrefixSkipList = classOf[KeyValueBehavior[_, _]].getName :: Nil

  /**
   * Create a `Behavior` for a persistent actor with key-value storage of its state.
   *
   * @param persistenceId stable unique identifier for the `KeyValueBehavior`
   * @param emptyState the intial state for the entity before any state has been stored
   * @param commandHandler map commands to effects e.g. persisting state, replying to commands
   */
  def apply[Command, State](
      persistenceId: PersistenceId,
      emptyState: State,
      commandHandler: (State, Command) => Effect[State]): KeyValueBehavior[Command, State] = {
    val loggerClass = LoggerClass.detectLoggerClassFromStack(classOf[KeyValueBehavior[_, _]], logPrefixSkipList)
    KeyValueBehaviorImpl(persistenceId, emptyState, commandHandler, loggerClass)
  }

  /**
   * Create a `Behavior` for a persistent actor that is enforcing that replies to commands are not forgotten.
   * Then there will be compilation errors if the returned effect isn't a [[ReplyEffect]], which can be
   * created with [[Effect.reply]], [[Effect.noReply]], [[EffectBuilder.thenReply]], or [[EffectBuilder.thenNoReply]].
   */
  def withEnforcedReplies[Command, State](
      persistenceId: PersistenceId,
      emptyState: State,
      commandHandler: (State, Command) => ReplyEffect[State]): KeyValueBehavior[Command, State] = {
    val loggerClass = LoggerClass.detectLoggerClassFromStack(classOf[KeyValueBehavior[_, _]], logPrefixSkipList)
    KeyValueBehaviorImpl(persistenceId, emptyState, commandHandler, loggerClass)
  }

  /**
   * The `CommandHandler` defines how to act on commands. A `CommandHandler` is
   * a function:
   *
   * {{{
   *   (State, Command) => Effect[State]
   * }}}
   *
   * The [[CommandHandler#command]] is useful for simple commands that don't need the state
   * and context.
   */
  object CommandHandler {

    /**
     * Convenience for simple commands that don't need the state and context.
     *
     * @see [[Effect]] for possible effects of a command.
     */
    def command[Command, State](commandHandler: Command => Effect[State]): (State, Command) => Effect[State] =
      (_, cmd) => commandHandler(cmd)

  }

  /**
   * The last sequence number that was persisted, can only be called from inside the handlers of a `KeyValueBehavior`
   */
  def lastSequenceNumber(context: ActorContext[_]): Long = {
    @tailrec
    def extractConcreteBehavior(beh: Behavior[_]): Behavior[_] =
      beh match {
        case interceptor: InterceptorImpl[_, _] => extractConcreteBehavior(interceptor.nestedBehavior)
        case concrete                           => concrete
      }

    extractConcreteBehavior(context.currentBehavior) match {
      case w: Running.WithSeqNrAccessible => w.currentSequenceNumber
      case s =>
        throw new IllegalStateException(s"Cannot extract the lastSequenceNumber in state ${s.getClass.getName}")
    }
  }

}

/**
 * Further customization of the `KeyValueBehavior` can be done with the methods defined here.
 *
 * Not for user extension
 */
@DoNotInherit trait KeyValueBehavior[Command, State] extends DeferredBehavior[Command] {

  def persistenceId: PersistenceId

  /**
   * Allows the `KeyValueBehavior` to react on signals.
   *
   * The regular lifecycle signals can be handled as well as `KeyValueBehavior` specific signals
   * (recovery related). Those are all subtypes of [[akka.persistence.typed.keyvalue.KeyValueSignal]]
   */
  def receiveSignal(signalHandler: PartialFunction[(State, Signal), Unit]): KeyValueBehavior[Command, State]

  /**
   * @return The currently defined signal handler or an empty handler if no custom handler previously defined
   */
  def signalHandler: PartialFunction[(State, Signal), Unit]

  /**
   * Change the `KeyValueStore` plugin id that this actor should use.
   */
  def withKeyValueStorePluginId(id: String): KeyValueBehavior[Command, State]

  /**
   * The tag that can used in persistence query
   */
  def withTag(tag: String): KeyValueBehavior[Command, State]

  /**
   * Transform the state to another type before giving to the store. Can be used to transform older
   * state types into the current state type e.g. when migrating from Persistent FSM to Typed KeyValueBehavior.
   */
  def snapshotAdapter(adapter: SnapshotAdapter[State]): KeyValueBehavior[Command, State]

  /**
   * Back off strategy for persist failures.
   *
   * Specifically BackOff to prevent resume being used. Resume is not allowed as
   * it will be unknown if the state has been persisted.
   *
   * This supervision is only around the `KeyValueBehavior` not any outer setup/withTimers
   * block. If using restart, any actions e.g. scheduling timers, can be done on the PreRestart
   *
   * If not specified the actor will be stopped on failure.
   */
  def onPersistFailure(backoffStrategy: BackoffSupervisorStrategy): KeyValueBehavior[Command, State]

}
