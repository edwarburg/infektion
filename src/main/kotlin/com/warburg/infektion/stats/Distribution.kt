package com.warburg.infektion.stats

import java.time.Duration
import java.util.*
/**
 * @author ewarburg
 */
interface Distribution<T : Any> {
    fun sample(rng: Random): T
}

class NormalDistribution(val mean: Double, val stddev: Double) : Distribution<Double> {
    override fun sample(rng: Random): Double = this.mean + rng.nextGaussian() * this.stddev
}

class NormalDurationDistribution(val mean: Duration, val stddev: Duration) : Distribution<Duration> {
    override fun sample(rng: Random): Duration = Duration.ofMillis(this.mean.toMillis() + (rng.nextGaussian() * this.stddev.toMillis()).toLong())
}

class UniformDoubleDistribution(val min: Double, val max: Double) : Distribution<Double> {
    private val diff = this.max - this.min

    // TODO this might return max exactly if `rng.nextDouble()` is 1
    override fun sample(rng: Random): Double = this.min + (rng.nextDouble() * this.diff)
}

class UniformIntDistribution(val min: Int, val max: Int) : Distribution<Int> {
    private val diff = this.max - this.min
    override fun sample(rng: Random): Int = rng.nextInt(this.diff) + this.min
}

class RateDistribution(val rate: PartPer10K): Distribution<Boolean> {
    override fun sample(rng: Random): Boolean = rng.nextInt(10000) < this.rate.n
}

open class HalfOpenRange<T : Comparable<T>>(val from: T, val to: T) {
    init {
        if (this.from >= this.to) {
            throw IllegalArgumentException("Start value must be before end value. Got start value: ${this.from} and end value: ${this.to}")
        }
    }

    fun contains(value: T): Boolean = this.from <= value && this.to > value

    fun overlapsWith(that: HalfOpenRange<T>): Boolean =
    // [  )
        // [    )
        this.from == that.from
                // [   )
                //   [ )
                || this.to == that.to
                // [    )
                //   [    )
                || this.from < that.from && this.to > that.from
                //   [     )
                // [     )
                || this.from < that.to && this.to > that.to
                // [         )
                //    [  }
                || this.from < that.from && this.to > that.to
                //    [  }
                // [         )
                || this.from > that.from && this.to < that.to

    override fun equals(other: Any?): Boolean {
        return other is HalfOpenRange<*> && this.from == other.from && this.to == other.to
    }

    override fun hashCode(): Int = Objects.hash(this.from, this.to)

    override fun toString(): String = "[${this.from}, ${this.to})"
}

fun Pair<Int, Int>.asHalfOpen(): HalfOpenRange<Int> = HalfOpenRange(this.first, this.second)

inline class PartPer10K(val n: Int) {
}

fun PartPer10K.percentageString(): String = "${this.n / 100}.${this.n % 100}%"

fun Int.per10K(): PartPer10K = PartPer10K(this)
fun Int.per1K(): PartPer10K = PartPer10K(this * 10)
fun Int.percent(): PartPer10K = PartPer10K(this * 100)

class HistogramDistribution<T : Comparable<T>>(
    bins: List<Pair<HalfOpenRange<T>, PartPer10K>>,
    private val sampler: (HalfOpenRange<T>) -> Distribution<T>
) : Distribution<T> {
    val cutoffs: IntArray
    val distributions: Array<Distribution<T>>
init {
        val sortedBins = bins.sortedBy { it.first.from }
        var parts = 0
        val numBins = sortedBins.size

        for (i in 0 until numBins) {
            val curr = sortedBins[i]
            val next = if (i + 1 < numBins) sortedBins[i + 1] else null
            if (next != null && curr.first.to != next.first.from) {
                throw IllegalArgumentException("Gap in bins: ${curr.first.to} to ${next.first.from} is not covered")
            }
            parts += curr.second.n
        }
        if (parts != 10000) {
            throw IllegalArgumentException("Bins must add up to exactly 100%. Got: ${parts.per10K().percentageString()}")
        }
        this.cutoffs = IntArray(numBins)
        for (i in 0 until numBins) {
            this.cutoffs[i] = sortedBins[i].second.n
            if (i > 0) {
                this.cutoffs[i] += this.cutoffs[i - 1]
            }
        }
        this.distributions = Array(numBins) { i -> this.sampler(sortedBins[i].first) }
    }

    override fun sample(rng: Random): T {
        val index = rng.nextInt(10000)
        this.cutoffs.forEachIndexed { i, cutoff ->
            if (cutoff > index) {
                return this.distributions[i].sample(rng)
            }
        }
        // should be unreachable assuming our constructor validated this right...
        throw IllegalStateException("Sample not within range - histogram distribution is not configured properly")
    }
}


