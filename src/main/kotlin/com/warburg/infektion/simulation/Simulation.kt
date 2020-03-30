package com.warburg.infektion.simulation

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.SetMultimap
import com.google.common.collect.Sets
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.HashMap

class Tick(
    val count: Long,
    val startingDateTime: LocalDateTime,
    val currentDateTime: LocalDateTime
) {
    val elapsed: Duration by lazy { Duration.between(this.startingDateTime, this.currentDateTime) }
}

fun Tick.tick(duration: Duration): Tick = Tick(this.count + 1, this.startingDateTime, this.currentDateTime + duration)

data class Infection(val from: CitizenId, val startingDateTime: LocalDateTime, val endingDateTime: LocalDateTime)

class CitizenState(
    val citizen: Citizen,
    var infectionState: InfectionState = InfectionState.Susceptible,
    var infections: MutableList<Infection> = mutableListOf(),
    var isAlive: Boolean = true
) {
    override fun toString(): String = "State(${this.citizen}, ${this.infectionState}, ${if (this.isAlive) "Alive" else "Dead"})"
}

interface SimulationListener {
    fun beginSimulation(simulation: Simulation)
    fun endSimulation(simulation: Simulation)

    fun beginTick(tick: Tick, simulation: Simulation)
    fun endTick(tick: Tick, simulation: Simulation)

    fun beginRecuperationPhase(simulation: Simulation)
    fun onRecuperation(citizen: CitizenId, simulation: Simulation)
    fun endRecuperationPhase(simulation: Simulation)

    fun beginKillPhase(simulation: Simulation)
    fun onKill(citizen: CitizenId, simulation: Simulation)
    fun endKillPhase(simulation: Simulation)

    fun beginRelocationPhase(simulation: Simulation)
    fun onRelocation(citizen: CitizenId, oldLocation: Location?, newLocation: Location?, simulation: Simulation)
    fun endRelocationPhase(simulation: Simulation)

    fun beginInteractionPhase(simulation: Simulation)
    fun onInteraction(interaction: Interaction, simulation: Simulation)
    fun endInteractionPhase(simulation: Simulation)
}

interface NoOpSimulationListener : SimulationListener {
    override fun beginSimulation(simulation: Simulation) {
    }

    override fun endSimulation(simulation: Simulation) {
    }

    override fun beginTick(tick: Tick, simulation: Simulation) {
    }

    override fun endTick(tick: Tick, simulation: Simulation) {
    }

    override fun beginRecuperationPhase(simulation: Simulation) {
    }

    override fun onRecuperation(citizen: CitizenId, simulation: Simulation) {
    }

    override fun endRecuperationPhase(simulation: Simulation) {
    }

    override fun beginKillPhase(simulation: Simulation) {
    }

    override fun onKill(citizen: CitizenId, simulation: Simulation) {
    }

    override fun endKillPhase(simulation: Simulation) {
    }

    override fun beginRelocationPhase(simulation: Simulation) {
    }

    override fun onRelocation(
        citizen: CitizenId,
        oldLocation: Location?,
        newLocation: Location?,
        simulation: Simulation
    ) {
    }

    override fun endRelocationPhase(simulation: Simulation) {
    }

    override fun beginInteractionPhase(simulation: Simulation) {
    }

    override fun onInteraction(interaction: Interaction, simulation: Simulation) {
    }

    override fun endInteractionPhase(simulation: Simulation) {
    }
}

/**
 * @author ewarburg
 */
class Simulation(
    val citizens: Set<Citizen>,
    val locations: Set<Location>,
    val diseaseModel: DiseaseModel,
    val peopleMover: PeopleMover,
    val initialInfections: Set<CitizenId> = emptySet(),
    val startingTime: LocalDateTime = LocalDateTime.of(2020, 1, 1, 0, 0, 0),
    val ticksPerDay: Int = 24,
    private val random: Random = Random(),
    private val listeners: Collection<SimulationListener> = emptyList()
) {
    private var _currentTick: Tick = Tick(1, this.startingTime, this.startingTime)
    val currentTick: Tick
        get() = _currentTick
    private val _citizensByLocation: SetMultimap<Location, Citizen> = LinkedHashMultimap.create(this.locations.size, (this.locations.size / this.citizens.size) + 1)
    val citizensByLocation: Map<Location, Collection<Citizen>>
        get() = _citizensByLocation.asMap()
    private val _citizenLocations: MutableMap<Citizen, Location> = HashMap(this.citizens.size)
    val citizenLocations: Map<Citizen, Location>
        get() = _citizenLocations
    val citizenStates: Map<CitizenId, CitizenState> = this.citizens.associate { it.id to CitizenState(it) }
    private val modelContext: ModelContext = ModelContext(this.random, this.ticksPerDay)

    private val patient0 = Citizen(CitizenId("Patient 0"), 20, Sex.Male)
    init {
        for (citizen in this.initialInfections) {
            val initialInfectionCitizenState = this.citizenStates.getValue(citizen)
            infect(this.patient0.id, initialInfectionCitizenState, this.diseaseModel.infectionDuration.sample(this.modelContext.rng))
        }
    }

    fun runFor(duration: Duration) {
        notify { it.beginSimulation(this) }
        while (this._currentTick.elapsed < duration) {
            tick()
        }
        notify { it.endSimulation(this) }
    }

    private inline fun notify(action: (SimulationListener) -> Unit) {
        this.listeners.forEach(action)
    }

    private fun tick() {
        notify { it.beginTick(this._currentTick, this) }

        recuperate()
        kill()
        relocateCitizens()
        interact()

        notify { it.endTick(this._currentTick, this) }

        this._currentTick = this._currentTick.tick(Duration.ofDays(1).dividedBy(this.ticksPerDay.toLong()))
    }

    private fun recuperate() {
        notify { it.beginRecuperationPhase(this) }

        for (state in this.citizenStates.values) {
            if (state.isAlive
                && state.infectionState == InfectionState.Infected
                && state.infections.last().endingDateTime <= this._currentTick.currentDateTime
            ) {
                val newInfectionState = this.diseaseModel.recovered(state, this.modelContext)
                if (newInfectionState == InfectionState.Recovered) {
                    notify { it.onRecuperation(state.citizen.id, this) }
                }
                state.infectionState = newInfectionState

            }
        }

        notify { it.endRecuperationPhase(this) }
    }

    private fun kill() {
        notify { it.beginKillPhase(this) }

        for (citizenState in this.citizenStates.values) {
            if (citizenState.isAlive && citizenState.infectionState == InfectionState.Infected) {
                val currentInfection = citizenState.infections.last()
                if (this.diseaseModel.checkDeath(citizenState, Duration.between(currentInfection.startingDateTime, this.currentTick.currentDateTime), this.modelContext)) {
                    citizenState.isAlive = false
                    val lastLocation = this.citizenLocations[citizenState.citizen]
                    lastLocation?.let {
                        this._citizensByLocation.remove(lastLocation, citizenState.citizen)
                    }
                    notify { it.onKill(citizenState.citizen.id, this) }
                }
            }
        }

        notify { it.endKillPhase(this) }
    }

    private fun relocateCitizens() {
        notify { it.beginRelocationPhase(this) }

        for (citizen in this.citizens) {
            val oldLocation = this._citizenLocations[citizen]
            val newLocation = this.peopleMover.move(citizen, oldLocation, this.currentTick)
            if (oldLocation != newLocation) {
                notify { it.onRelocation(citizen.id, oldLocation, newLocation, this) }
            }
            if (newLocation != null) {
                this._citizenLocations[citizen] = newLocation
                this._citizensByLocation.put(newLocation, citizen)
            } else {
                this._citizenLocations.remove(citizen)
            }
            if (oldLocation != null) {
                this._citizensByLocation.remove(oldLocation, citizen)
            }
        }

        notify { it.endRelocationPhase(this) }
    }

    private fun interact() {
        notify { it.beginInteractionPhase(this) }

        for ((_, citizens) in this._citizensByLocation.asMap()) {
           val interactions = citizens.uniquePairs().map { (citizen1, citizen2) ->
               val firstCitizenState = this.citizenStates.getValue(citizen1.id)
               val secondCitizenState = this.citizenStates.getValue(citizen2.id)
               this.diseaseModel.interact(firstCitizenState, secondCitizenState, this.modelContext)
           }

            for (interaction in interactions) {
                val forceHandleAll = when (interaction) {
                    is Infected -> {
                        if (interaction.target.infectionState != InfectionState.Infected) {
                            // it's possible to double-infect if multiple people interact with the same person during a tick,
                            // but we'll say whoever got there first was the one who did the infecting
                            infect(
                                interaction.source.citizen.id,
                                interaction.target,
                                interaction.infectionDuration
                            )
                            notify { it.onInteraction(interaction, this) }
                        }
                        Unit
                    }
                    is NothingChanged -> {
                        notify { it.onInteraction(interaction, this) }
                    }
                }
            }
        }

        notify { it.endInteractionPhase(this) }
    }

    private fun infect(
        source: CitizenId,
        target: CitizenState,
        duration: Duration
    ) {
        target.infectionState = InfectionState.Infected
        val infection = Infection(
            source as CitizenId,
            this._currentTick.currentDateTime,
            this._currentTick.currentDateTime + duration
        )
        target.infections.add(infection)
    }
}

private fun <E> Collection<E>.uniquePairs() : Collection<Pair<E, E>> = if (this.size < 2) {
    emptyList()
} else {
    Sets.combinations(this.toSet(), 2)
        // with one person in a given location, may end up with sets of size <2
        .filter { it.size == 2 }
        .map {
            val iterator = it.iterator()
            return@map iterator.next() to iterator.next()
        }
}
