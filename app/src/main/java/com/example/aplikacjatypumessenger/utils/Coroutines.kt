package com.example.aplikacjatypumessenger.utils

import kotlin.coroutines.cancellation.CancellationException

/** Jak `runCatching`, ale nie połyka anulowania korutyny. */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
