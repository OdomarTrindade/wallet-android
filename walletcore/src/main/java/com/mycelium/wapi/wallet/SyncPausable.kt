package com.mycelium.wapi.wallet

import kotlin.concurrent.thread

interface SyncPausable {
    /**
     * Interrupt gracefully ongoing sync. This method is blocking until it takes effect.
     */
    fun interruptSync()

    /**
     * @return true if syncing was not interrupted by interruptSync()
     */
    fun maySync(): Boolean
}

abstract class SyncPausableContext: SyncPausable {
    @Volatile
    private var maySync = true

    override fun interruptSync() {
        maySync = false
        synchronized(this) {}
        maySync = true
    }

    override fun maySync() = maySync
}


abstract class SyncPausableAccount(val syncPausableContext: SyncPausableContext): SyncPausable {
    override fun interruptSync() {
        syncPausableContext.interruptSync()
    }

    override fun maySync() = syncPausableContext.maySync()
}

fun Collection<WalletAccount<*>>.interruptSync() {
    map { thread { it.interruptSync() } }.map { it.join() }
}