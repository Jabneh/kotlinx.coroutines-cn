/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.reactor

import kotlinx.coroutines.*
import kotlinx.coroutines.reactive.*
import org.hamcrest.core.*
import org.junit.*
import org.junit.Test
import kotlin.test.*

class FluxTest : TestBase() {
    @Test
    fun testBasicSuccess() = runBlocking {
        expect(1)
        val flux = flux {
            expect(4)
            send("OK")
        }
        expect(2)
        flux.subscribe { value ->
            expect(5)
            Assert.assertThat(value, IsEqual("OK"))
        }
        expect(3)
        yield() // to started coroutine
        finish(6)
    }

    @Test
    fun testBasicFailure() = runBlocking {
        expect(1)
        val flux = flux<String>(NonCancellable) {
            expect(4)
            throw RuntimeException("OK")
        }
        expect(2)
        flux.subscribe({
            expectUnreached()
        }, { error ->
            expect(5)
            Assert.assertThat(error, IsInstanceOf(RuntimeException::class.java))
            Assert.assertThat(error.message, IsEqual("OK"))
        })
        expect(3)
        yield() // to started coroutine
        finish(6)
    }

    @Test
    fun testBasicUnsubscribe() = runBlocking {
        expect(1)
        val flux = flux<String> {
            expect(4)
            yield() // back to main, will get cancelled
            expectUnreached()
        }
        expect(2)
        val sub = flux.subscribe({
            expectUnreached()
        }, {
            expectUnreached()
        })
        expect(3)
        yield() // to started coroutine
        expect(5)
        sub.dispose() // will cancel coroutine
        yield()
        finish(6)
    }

    @Test
    fun testCancelsParentOnFailure() = runTest(
        expected = { it is RuntimeException && it.message == "OK" }
    ) {
        // has parent, so should cancel it on failure
        flux<Unit> {
            throw RuntimeException("OK")
        }.subscribe(
            { expectUnreached() },
            { assert(it is RuntimeException) }
        )
    }

    @Test
    fun testNotifyOnceOnCancellation() = runTest {
        expect(1)
        val observable =
            flux {
                expect(5)
                send("OK")
                try {
                    delay(Long.MAX_VALUE)
                } catch (e: CancellationException) {
                    expect(11)
                }
            }
            .doOnNext {
                expect(6)
                assertEquals("OK", it)
            }
            .doOnCancel {
                expect(10) // notified once!
            }
        expect(2)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            expect(3)
            observable.consumeEach {
                expect(8)
                assertEquals("OK", it)
            }
        }
        expect(4)
        yield() // to observable code
        expect(7)
        yield() // to consuming coroutines
        expect(9)
        job.cancel()
        job.join()
        finish(12)
    }

    @Test
    fun testFailingConsumer() = runTest {
        val pub = flux {
            repeat(3) {
                expect(it + 1) // expect(1), expect(2) *should* be invoked
                send(it)
            }
        }
        try {
            pub.consumeEach {
                throw TestException()
            }
        } catch (e: TestException) {
            finish(3)
        }
    }
}