package com.warburg.infektion.simulation.listeners

import com.warburg.infektion.simulation.*
import java.time.Duration

class TracingListener(val perf: Boolean) : SimulationListener {
    var simStart: Long = 0
    var lastTickStart: Long = 0

    override fun beginSimulation(simulation: Simulation) {
        this.simStart = System.nanoTime()

        prominent("begin simulation ${simulation.currentTick.startingDateTime.toLocalDate()}")

        for (citizenState in simulation.citizenStates.values) {
            if (citizenState.infectionState == InfectionState.Infected) {
                event("$virus ${citizenState.citizen} started off infected and it will end at ${endDate(simulation,
                    Duration.between(simulation.startingTime, citizenState.infections.last().endingDateTime)
                )})", simulation)
            }
        }

        summarizeStates(simulation)
    }

    override fun endSimulation(simulation: Simulation) {
        prominent("end simulation" + if (this.perf) "(took ${Duration.ofNanos(System.nanoTime() - this.simStart).pretty()})" else "")
    }

    override fun beginTick(tick: Tick, simulation: Simulation) {
        this.lastTickStart = System.nanoTime()

        if (tick.currentDateTime.hour == 0) {
            prominent("begin day: ${tick.currentDateTime.toLocalDate()} (tick ${tick.count})")
        }
    }

    override fun endTick(tick: Tick, simulation: Simulation) {
        if (this.perf) {
            prominent("end day (took ${Duration.ofNanos(System.nanoTime() - this.lastTickStart).pretty()})")
        }
        if (tick.currentDateTime.hour == 23) {
            summarizeStates(simulation)
        }
    }

    private fun summarizeStates(simulation: Simulation) {
        val citizenStateCounts =
            simulation.citizenStates.values.filter { it.isAlive }.groupBy { it.infectionState }.mapValues { it.value.size }
        for (state in InfectionState.values()) {
            indented("${state.name}: ${citizenStateCounts[state] ?: 0}", 2)
        }

        val livingAndDead = simulation.citizenStates.values.groupBy { it.isAlive }.mapValues { it.value.size }
        indented("Living: ${livingAndDead[true] ?: 0}", 2)
        indented("Dead: ${livingAndDead[false] ?: 0}", 2)
    }

    override fun beginRecuperationPhase(simulation: Simulation) {
        sectionBorder("begin recuperation")
    }

    override fun onRecuperation(citizen: CitizenId, simulation: Simulation) {
        event("$thumbsUp $citizen recovered!", simulation)
    }

    override fun endRecuperationPhase(simulation: Simulation) {
        sectionBorder("end recuperation")
    }

    override fun beginKillPhase(simulation: Simulation) {
        sectionBorder("begin killing")
    }

    override fun onKill(citizen: CitizenId, simulation: Simulation) {
        event("$skull $citizen died :(", simulation)
    }

    override fun endKillPhase(simulation: Simulation) {
        sectionBorder("end killing")
    }

    override fun beginRelocationPhase(simulation: Simulation) {
        sectionBorder("begin relocation")
    }

    override fun onRelocation(
        citizen: CitizenId,
        oldLocation: Location?,
        newLocation: Location?,
        simulation: Simulation
    ) {
        when {
            oldLocation == null && newLocation == null -> return
            oldLocation == null -> event("$baby $citizen spawned at $newLocation", simulation)
            newLocation == null -> event("$ghost ️$citizen was at $oldLocation but disappeared into the ether. Spooky!", simulation)
            else -> return //event("$car $citizen went from $oldLocation to $newLocation", simulation)
        }
    }

    override fun endRelocationPhase(simulation: Simulation) {
        sectionBorder("end relocation")
    }

    override fun beginInteractionPhase(simulation: Simulation) {
        sectionBorder("begin interaction")
    }

    override fun onInteraction(interaction: Interaction, simulation: Simulation) {
        return when (interaction) {
            is Infected -> {
                event("$virus ${interaction.source.citizen} infected ${interaction.target.citizen} and it will end at ${endDate(simulation, interaction.infectionDuration)})", simulation)
            }
            else -> {}
        }
    }

    private fun endDate(simulation: Simulation, duration: Duration): String = "${simulation.currentTick.currentDateTime + duration} (${duration.pretty()}"

    override fun endInteractionPhase(simulation: Simulation) {
        sectionBorder("end interaction")
    }

    private val baby = "\uD83D\uDC76"
    private val ghost = "\uD83D\uDC7B"
    private val car = "\uD83D\uDE97"
    private val virus = "\uD83E\uDDA0"
    private val thumbsUp = "\uD83D\uDC4D"
    private val skull = "\uD83D\uDC80"

    private fun Duration.pretty(): String = if (this.toMinutes() < 1) {
        "${this.toSecondsPart()}s ${this.toMillisPart()}ms ${this.toNanosPart()}ns"
    } else {
        "${this.toDaysPart()}d ${this.toHoursPart()}h ${this.toMinutesPart()}m ${this.toSecondsPart()}s"
    }

    private fun prominent(msg: String) {
        println("---------- $msg ----------")
    }
    private fun event(msg: String, simulation: Simulation) = indented("(${simulation.currentTick.currentDateTime.toLocalTime()}) $msg", 1)
    private fun sectionBorder(msg: String) {
//        indented(msg, 1)
    }
    private fun indented(msg: String, times: Int = 1) {
        for (i in 0 until times) {
            print("    ")
        }
        println(msg)
    }

    companion object {
        fun create(perf: Boolean = false): TracingListener = TracingListener(perf)
    }
}