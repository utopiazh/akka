/*
 * Copyright (C) 2017-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.persistence.typed.keyvalue.internal

import org.slf4j.LoggerFactory

import akka.actor.typed
import akka.actor.typed.ActorRef
import akka.actor.typed.BackoffSupervisorStrategy
import akka.actor.typed.Behavior
import akka.actor.typed.BehaviorInterceptor
import akka.actor.typed.PostStop
import akka.actor.typed.Signal
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.internal.ActorContextImpl
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.annotation._
import akka.persistence.RecoveryPermitter
import akka.persistence.typed.keyvalue.scaladsl._
import akka.persistence.keyvaluestore.scaladsl.GetObjectResult
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.SnapshotAdapter
import akka.util.unused

@InternalApi
private[akka] object KeyValueBehaviorImpl {

  /**
   * Used by KeyValueBehaviorTestKit to retrieve the `persistenceId`.
   */
  final case class GetPersistenceId(replyTo: ActorRef[PersistenceId]) extends Signal

  /**
   * Used by KeyValueBehaviorTestKit to retrieve the state.
   * Can't be a Signal because those are not stashed.
   */
  final case class GetState[State](replyTo: ActorRef[State]) extends InternalProtocol

}

@InternalApi
private[akka] final case class KeyValueBehaviorImpl[Command, State](
    persistenceId: PersistenceId,
    emptyState: State,
    commandHandler: KeyValueBehavior.CommandHandler[Command, State],
    loggerClass: Class[_],
    keyValueStorePluginId: Option[String] = None,
    tag: String = "",
    snapshotAdapter: SnapshotAdapter[State] = NoOpSnapshotAdapter.instance[State],
    supervisionStrategy: SupervisorStrategy = SupervisorStrategy.stop,
    override val signalHandler: PartialFunction[(State, Signal), Unit] = PartialFunction.empty)
    extends KeyValueBehavior[Command, State] {

  if (persistenceId eq null)
    throw new IllegalArgumentException("persistenceId must not be null")

  // Don't use it directly, but instead call internalLogger() (see below)
  private val loggerForInternal = LoggerFactory.getLogger(this.getClass)

  override def apply(context: typed.TypedActorContext[Command]): Behavior[Command] = {
    val ctx = context.asScala
    val hasCustomLoggerName = ctx match {
      case internalCtx: ActorContextImpl[_] => internalCtx.hasCustomLoggerName
      case _                                => false
    }
    if (!hasCustomLoggerName) ctx.setLoggerName(loggerClass)
    val settings = KeyValueSettings(ctx.system, keyValueStorePluginId.getOrElse(""))

    // stashState outside supervise because StashState should survive restarts due to persist failures
    val stashState = new StashState(ctx.asInstanceOf[ActorContext[InternalProtocol]], settings)

    // This method ensures that the MDC is set before we use the internal logger
    def internalLogger() = {
      if (settings.useContextLoggerForInternalLogging) ctx.log
      else {
        // MDC is cleared (if used) from aroundReceive in ActorAdapter after processing each message,
        // but important to call `context.log` to mark MDC as used
        ctx.log
        loggerForInternal
      }
    }

    val actualSignalHandler: PartialFunction[(State, Signal), Unit] = signalHandler.orElse {
      // default signal handler is always the fallback
      case (_, KeyValueBehaviorImpl.GetPersistenceId(replyTo)) => replyTo ! persistenceId
    }

    // do this once, even if the actor is restarted
    initialize(context.asScala)

    Behaviors
      .supervise {
        Behaviors.setup[Command] { _ =>
          val keyValueSetup = new BehaviorSetup(
            ctx.asInstanceOf[ActorContext[InternalProtocol]],
            persistenceId,
            emptyState,
            commandHandler,
            actualSignalHandler,
            tag,
            snapshotAdapter,
            holdingRecoveryPermit = false,
            settings = settings,
            stashState = stashState,
            internalLoggerFactory = () => internalLogger())

          // needs to accept Any since we also can get messages from outside
          // not part of the user facing Command protocol
          def interceptor: BehaviorInterceptor[Any, InternalProtocol] = new BehaviorInterceptor[Any, InternalProtocol] {

            import BehaviorInterceptor._
            override def aroundReceive(
                ctx: typed.TypedActorContext[Any],
                msg: Any,
                target: ReceiveTarget[InternalProtocol]): Behavior[InternalProtocol] = {
              val innerMsg = msg match {
                case RecoveryPermitter.RecoveryPermitGranted => InternalProtocol.RecoveryPermitGranted
                case internal: InternalProtocol              => internal // such as RecoveryTimeout
                case cmd                                     => InternalProtocol.IncomingCommand(cmd.asInstanceOf[Command])
              }
              target(ctx, innerMsg)
            }

            override def aroundSignal(
                ctx: typed.TypedActorContext[Any],
                signal: Signal,
                target: SignalTarget[InternalProtocol]): Behavior[InternalProtocol] = {
              if (signal == PostStop) {
                keyValueSetup.cancelRecoveryTimer()
                // clear stash to be GC friendly
                stashState.clearStashBuffers()
              }
              target(ctx, signal)
            }

            override def toString: String = "KeyValueBehaviorInterceptor"
          }

          Behaviors.intercept(() => interceptor)(RequestingRecoveryPermit(keyValueSetup)).narrow
        }

      }
      .onFailure[KeyValueStoreException](supervisionStrategy)
  }

  @InternalStableApi
  private[akka] def initialize(@unused context: ActorContext[_]): Unit = ()

  override def receiveSignal(handler: PartialFunction[(State, Signal), Unit]): KeyValueBehavior[Command, State] =
    copy(signalHandler = handler)

  override def withKeyValueStorePluginId(id: String): KeyValueBehavior[Command, State] = {
    require(id != null, "key-value plugin id must not be null; use empty string for 'default' key-value store")
    copy(keyValueStorePluginId = if (id != "") Some(id) else None)
  }

  override def withTag(tag: String): KeyValueBehavior[Command, State] =
    copy(tag = tag)

  override def snapshotAdapter(adapter: SnapshotAdapter[State]): KeyValueBehavior[Command, State] =
    copy(snapshotAdapter = adapter)

  override def onPersistFailure(backoffStrategy: BackoffSupervisorStrategy): KeyValueBehavior[Command, State] =
    copy(supervisionStrategy = backoffStrategy)

}

/** Protocol used internally by the KeyValueBehavior. */
@InternalApi private[akka] sealed trait InternalProtocol
@InternalApi private[akka] object InternalProtocol {
  case object RecoveryPermitGranted extends InternalProtocol
  final case class GetSuccess[S](result: GetObjectResult[S]) extends InternalProtocol
  final case class GetFailure(cause: Throwable) extends InternalProtocol
  case object UpsertSuccess extends InternalProtocol
  final case class UpsertFailure(cause: Throwable) extends InternalProtocol
  case object RecoveryTimeout extends InternalProtocol
  final case class IncomingCommand[C](c: C) extends InternalProtocol

}
