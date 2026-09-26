package com.casinomaster

import org.junit.Assert.*
import org.junit.Test

class AutomationEngineTest {
    private class FakeClock(var now: Long = 0L) { fun get() = now }
    private class FakeController : DemoGameController({}) {
        var bets = 0
        var collects = 0
        override fun placeBet(amount: Double): Boolean { bets++; return super.placeBet(amount) }
        override fun collect(multiplier: Double): Double? { collects++; return super.collect(multiplier) }
    }

    @Test fun countdownParsing() {
        val d = CountdownDetector()
        assertEquals(15, d.parse("15"))
        assertEquals(14, d.parse("14 seconds"))
        assertEquals(13, d.parse("13s"))
    }

    @Test fun multiplierParsing() {
        val d = MultiplierDetector()
        assertEquals(1.23, d.parse("1.23x")!!, 0.0001)
        assertEquals(1.50, d.parse("1.50x")!!, 0.0001)
        assertEquals(2.0, d.parse("2.00x")!!, 0.0001)
    }

    @Test fun countdown15TransitionsAndPlacesOneBet() {
        val clock = FakeClock()
        val c = FakeController()
        val machine = AutomationStateMachine(clock=clock::get, controller=c, logger=AutomationLogger { _,_,_,_,_,_,_,_,_-> })
        machine.start()
        machine.observe(GameObservation(countdown=16))
        machine.observe(GameObservation(countdown=15))
        machine.observe(GameObservation(countdown=15))
        assertEquals(1, c.bets)
        assertTrue(machine.snapshot().state == AutomationState.WAITING_FOR_ROUND)
    }

    @Test fun target150CollectsOnce() {
        val clock = FakeClock()
        val c = FakeController()
        val machine = AutomationStateMachine(clock=clock::get, controller=c, logger=AutomationLogger { _,_,_,_,_,_,_,_,_-> })
        machine.start()
        machine.observe(GameObservation(countdown=15))
        machine.observe(GameObservation(roundActive=true, multiplier=1.08))
        machine.observe(GameObservation(roundActive=true, multiplier=1.50))
        machine.observe(GameObservation(roundActive=true, multiplier=1.51))
        assertEquals(1, c.collects)
        assertEquals(AutomationState.TEST_COLLECTED, machine.snapshot().state)
    }

    @Test fun unreadableValuesDoNotAct() {
        val clock = FakeClock()
        val c = FakeController()
        val machine = AutomationStateMachine(clock=clock::get, controller=c, logger=AutomationLogger { _,_,_,_,_,_,_,_,_-> })
        machine.start()
        machine.observe(GameObservation())
        assertEquals(0, c.bets)
        assertEquals(AutomationState.WAITING_FOR_COUNTDOWN, machine.snapshot().state)
    }

    @Test fun cooldownCreatesNewRoundGuard() {
        val clock = FakeClock()
        val c = FakeController()
        val machine = AutomationStateMachine(clock=clock::get, controller=c, logger=AutomationLogger { _,_,_,_,_,_,_,_,_-> })
        machine.start()
        machine.observe(GameObservation(countdown=15))
        machine.observe(GameObservation(roundActive=true, multiplier=1.50))
        machine.observe(GameObservation(roundEnded=true))
        clock.now = 10_001
        machine.observe(GameObservation())
        assertEquals(AutomationState.WAITING_FOR_COUNTDOWN, machine.snapshot().state)
        machine.observe(GameObservation(countdown=15))
        assertEquals(2, c.bets)
    }
}
