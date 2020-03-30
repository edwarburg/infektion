package com.warburg.infektion.simulation

import com.warburg.infektion.stats.Distribution
import java.time.Duration
import java.util.*

class ModelContext(
    val rng: Random,
    val ticksPerDay: Int
)

/**
 * @author ewarburg
 */
interface DiseaseModel {
    fun interact(firstHost: CitizenState, secondHost: CitizenState, context: ModelContext): Interaction
    fun recovered(host: CitizenState, context: ModelContext): InfectionState

    fun checkDeath(host: CitizenState, timeInfected: Duration, context: ModelContext): Boolean = context.rng.nextDouble() < fatalityRate(host, timeInfected, context)
    // TODO fatality rate curve based on time elapsed
    fun fatalityRate(host: CitizenState, timeInfected: Duration, context: ModelContext): Double

    // TODO disease progression over time, eg, "symptomless", "mild", "moderate", "requires hospitalization", "lethal"?
    //   And have disease progression change transmission rate?

    val infectionDuration: Distribution<Duration>
    val transmissionRate: Double
}

sealed class Interaction
// TODO track infection location
data class Infected(val source: CitizenState, val target: CitizenState, val infectionDuration: Duration) : Interaction()
object NothingChanged : Interaction()

class SIRDiseaseModel(
    override val infectionDuration: Distribution<Duration>,
    override val transmissionRate: Double,
    private val fatalityRateFunction: (CitizenState, timeInfected: Duration, context: ModelContext) -> Double
) : DiseaseModel {
    override fun interact(firstHost: CitizenState, secondHost: CitizenState, context: ModelContext): Interaction {
        // handle immunities
        if (firstHost.infectionState == InfectionState.Recovered
            || secondHost.infectionState == InfectionState.Recovered
            || (firstHost.infectionState == secondHost.infectionState)) {
            return NothingChanged
        }

        // one or the other is infected and the other is susceptible
        // TODO factor in "heartiness" of target to change length/severity of infection?
        
        // only transmit according to infection rate
        if (context.rng.nextDouble() > this.transmissionRate) {
            return NothingChanged
        }

        if (firstHost.infectionState == InfectionState.Infected) {
            return Infected(firstHost, secondHost, this.infectionDuration.sample(context.rng))
        }

        return Infected(secondHost, firstHost, this.infectionDuration.sample(context.rng))
    }
    override fun recovered(host: CitizenState, context: ModelContext): InfectionState = when (val infectionState = host.infectionState) {
        InfectionState.Infected -> InfectionState.Recovered
        else -> infectionState
    }

    override fun fatalityRate(host: CitizenState, timeInfected: Duration, context: ModelContext): Double = this.fatalityRateFunction(host, timeInfected, context)
}

enum class Sex {
    Male,
    Female
}

enum class InfectionState {
    Susceptible,
    Infected,
    Recovered
}