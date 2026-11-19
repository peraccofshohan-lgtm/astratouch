package com.astratouch.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionEvaluatorTest {

    @Test
    fun `addition and subtraction`() {
        assertEquals(7.0, ExpressionEvaluator.evaluate("3+4").getOrThrow(), 0.0001)
        assertEquals(-1.0, ExpressionEvaluator.evaluate("3-4").getOrThrow(), 0.0001)
    }

    @Test
    fun `multiplication and division`() {
        assertEquals(12.0, ExpressionEvaluator.evaluate("3*4").getOrThrow(), 0.0001)
        assertEquals(2.5, ExpressionEvaluator.evaluate("5/2").getOrThrow(), 0.0001)
    }

    @Test
    fun `operator precedence`() {
        assertEquals(14.0, ExpressionEvaluator.evaluate("2+3*4").getOrThrow(), 0.0001)
        assertEquals(20.0, ExpressionEvaluator.evaluate("(2+3)*4").getOrThrow(), 0.0001)
    }

    @Test
    fun `exponentiation is right associative`() {
        assertEquals(512.0, ExpressionEvaluator.evaluate("2^3^2").getOrThrow(), 0.0001)
    }

    @Test
    fun `unary minus`() {
        assertEquals(-5.0, ExpressionEvaluator.evaluate("-5").getOrThrow(), 0.0001)
        assertEquals(-8.0, ExpressionEvaluator.evaluate("-(3+5)").getOrThrow(), 0.0001)
    }

    @Test
    fun `decimal percentages and unicode operators`() {
        assertEquals(0.5, ExpressionEvaluator.evaluate("1.5-1").getOrThrow(), 0.0001)
        assertEquals(8.0, ExpressionEvaluator.evaluate("2×4").getOrThrow(), 0.0001)
        assertEquals(4.0, ExpressionEvaluator.evaluate("8÷2").getOrThrow(), 0.0001)
        assertEquals(1.0, ExpressionEvaluator.evaluate("7%3").getOrThrow(), 0.0001)
    }

    @Test
    fun `division by zero fails clearly`() {
        val result = ExpressionEvaluator.evaluate("5/0")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("divide by zero", ignoreCase = true))
    }

    @Test
    fun `malformed input fails instead of guessing`() {
        assertTrue(ExpressionEvaluator.evaluate("2+").isFailure)
        assertTrue(ExpressionEvaluator.evaluate("(2+3").isFailure)
        assertTrue(ExpressionEvaluator.evaluate("abc").isFailure)
        assertTrue(ExpressionEvaluator.evaluate("2..3").isFailure)
    }
}
