/**
 * Copyright (C) 2009-2011 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.util

import java.util.concurrent.locks.{ ReentrantLock }
import java.util.concurrent.atomic.{ AtomicBoolean }

/**
 * @author <a href="http://jonasboner.com">Jonas Bon&#233;r</a>
 */
final class ReentrantGuard {
  final val lock = new ReentrantLock

  @inline
  final def withGuard[T](body: ⇒ T): T = {
    lock.lock
    try {
      body
    } finally {
      lock.unlock
    }
  }
}

/**
 * An atomic switch that can be either on or off
 */
class Switch(startAsOn: Boolean = false) {
  private val switch = new AtomicBoolean(startAsOn)

  protected def transcend(from: Boolean, action: ⇒ Unit): Boolean = synchronized {
    if (switch.compareAndSet(from, !from)) {
      try {
        action
      } catch {
        case e ⇒
          switch.compareAndSet(!from, from) // revert status
          throw e
      }
      true
    } else false
  }

  /**
   * Executes the provided action if the lock is on under a lock, so be _very_ careful with longrunning/blocking operations in it
   * Only executes the action if the switch is on, and switches it off immediately after obtaining the lock
   * Will switch it back on if the provided action throws an exception
   */
  def switchOff(action: ⇒ Unit): Boolean = transcend(from = true, action)

  /**
   * Executes the provided action if the lock is off under a lock, so be _very_ careful with longrunning/blocking operations in it
   * Only executes the action if the switch is off, and switches it on immediately after obtaining the lock
   * Will switch it back off if the provided action throws an exception
   */
  def switchOn(action: ⇒ Unit): Boolean = transcend(from = false, action)

  /**
   * Switches the switch off (if on), uses locking
   */
  def switchOff: Boolean = synchronized { switch.compareAndSet(true, false) }

  /**
   * Switches the switch on (if off), uses locking
   */
  def switchOn: Boolean = synchronized { switch.compareAndSet(false, true) }

  /**
   * Executes the provided action and returns its value if the switch is IMMEDIATELY on (i.e. no lock involved)
   */
  def ifOnYield[T](action: ⇒ T): Option[T] = {
    if (switch.get) Some(action)
    else None
  }

  /**
   * Executes the provided action and returns its value if the switch is IMMEDIATELY off (i.e. no lock involved)
   */
  def ifOffYield[T](action: ⇒ T): Option[T] = {
    if (!switch.get) Some(action)
    else None
  }

  /**
   * Executes the provided action and returns if the action was executed or not, if the switch is IMMEDIATELY on (i.e. no lock involved)
   */
  def ifOn(action: ⇒ Unit): Boolean = {
    if (switch.get) {
      action
      true
    } else false
  }

  /**
   * Executes the provided action and returns if the action was executed or not, if the switch is IMMEDIATELY off (i.e. no lock involved)
   */
  def ifOff(action: ⇒ Unit): Boolean = {
    if (!switch.get) {
      action
      true
    } else false
  }

  /**
   * Executes the provided action and returns its value if the switch is on, waiting for any pending changes to happen before (locking)
   * Be careful of longrunning or blocking within the provided action as it can lead to deadlocks or bad performance
   */
  def whileOnYield[T](action: ⇒ T): Option[T] = synchronized {
    if (switch.get) Some(action)
    else None
  }

  /**
   * Executes the provided action and returns its value if the switch is off, waiting for any pending changes to happen before (locking)
   * Be careful of longrunning or blocking within the provided action as it can lead to deadlocks or bad performance
   */
  def whileOffYield[T](action: ⇒ T): Option[T] = synchronized {
    if (!switch.get) Some(action)
    else None
  }

  /**
   * Executes the provided action and returns if the action was executed or not, if the switch is on, waiting for any pending changes to happen before (locking)
   * Be careful of longrunning or blocking within the provided action as it can lead to deadlocks or bad performance
   */
  def whileOn(action: ⇒ Unit): Boolean = synchronized {
    if (switch.get) {
      action
      true
    } else false
  }

  /**
   * Executes the provided action and returns if the action was executed or not, if the switch is off, waiting for any pending changes to happen before (locking)
   * Be careful of longrunning or blocking within the provided action as it can lead to deadlocks or bad performance
   */
  def whileOff(action: ⇒ Unit): Boolean = synchronized {
    if (switch.get) {
      action
      true
    } else false
  }

  /**
   * Executes the provided callbacks depending on if the switch is either on or off waiting for any pending changes to happen before (locking)
   * Be careful of longrunning or blocking within the provided action as it can lead to deadlocks or bad performance
   */
  def fold[T](on: ⇒ T)(off: ⇒ T) = synchronized {
    if (switch.get) on else off
  }

  /**
   * Returns whether the switch is IMMEDIATELY on (no locking)
   */
  def isOn = switch.get

  /**
   * Returns whether the switch is IMMEDDIATELY off (no locking)
   */
  def isOff = !isOn
}
