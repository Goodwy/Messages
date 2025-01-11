package com.goodwy.smsmessenger.extensions

import kotlin.math.roundToInt

/**
 * Returns the closest number divisible by [multipleOf].
 */
fun Int.roundToClosestMultipleOf(multipleOf: Int = 1): Int {
    return (toDouble() / multipleOf).roundToInt() * multipleOf
}
