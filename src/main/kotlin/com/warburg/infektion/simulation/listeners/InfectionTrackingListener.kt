package com.warburg.infektion.simulation.listeners

import com.warburg.infektion.simulation.*
import java.io.FileWriter
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

class InfectionTrackingListener : NoOpSimulationListener {
    val infections: MutableList<InfectionRecord> = mutableListOf()
    val finalStatus: MutableMap<CitizenId, InfectionState> = mutableMapOf()
    val dead: MutableSet<CitizenId> = mutableSetOf()

    override fun onRecuperation(citizen: CitizenId, simulation: Simulation) {
        this.finalStatus[citizen] = InfectionState.Recovered
    }

    override fun onInteraction(interaction: Interaction, simulation: Simulation) {
        when (interaction) {
            is Infected -> {
                this.infections.add(
                    InfectionRecord(
                        interaction.source.citizen,
                        interaction.target.citizen,
                        interaction.infectionDuration,
                        simulation.currentTick
                    )
                )
                this.finalStatus[interaction.target.citizen.id] = interaction.target.infectionState
            }
        }
    }

    override fun onKill(citizen: CitizenId, simulation: Simulation) {
        this.dead.add(citizen)
    }

    data class InfectionRecord(val source: Citizen, val target: Citizen, val duration: Duration, val tick: Tick)

    companion object {
        fun create(): InfectionTrackingListener = InfectionTrackingListener()
    }
}

enum class Style {
    Point,
    Circle
}

fun splatToGraphviz(outputPath: Path, listener: InfectionTrackingListener, style: Style = Style.Point) {
    val citizenNodeIds: Map<CitizenId, String> = listener.infections.asSequence().flatMap { sequenceOf(it.source, it.target) }.associate { it.id to Regex("[^a-zA-Z0-9]").replace(it.id.id, "_").toLowerCase() }
    FileWriter(outputPath.toFile()).use {
        it.appendln("digraph InfectionGraph {")
        it.appendln("    nodesep=.05;")

        for (infection in listener.infections) {
            it.appendln("    ${citizenNodeIds[infection.source.id]} -> ${citizenNodeIds[infection.target.id]} ;#[label=\"${infection.tick.currentDateTime}\"];")
        }

        for ((id, nodeId) in citizenNodeIds) {
            val color = listener.colorForFinalState(id)
            when (style) {
                Style.Point  -> it.appendln("    $nodeId [label=\"\" shape=\"point\" color=\"$color\" width=\"0.5\"];")
                Style.Circle -> it.appendln("    $nodeId [label=\"$id\" shape=\"circle\" style=filled fillcolor=\"$color\" width=\"0.5\"];")
            }
        }

        it.appendln("}")
    }
}

fun InfectionTrackingListener.colorForFinalState(id: CitizenId): String = if (this.dead.contains(id)) {
    "crimson"
} else when (this.finalStatus[id]!!) {
    InfectionState.Susceptible -> "darkgreen"
    InfectionState.Infected -> "darkorange"
    InfectionState.Recovered -> "darkgreen"
}

fun splatToGraphvizOnDesktop(
    infectionTracker: InfectionTrackingListener,
    style: Style = Style.Point
) {
    splatToGraphviz(Paths.get("/Users/ewarburg/Desktop/infektion_graph.dot"), infectionTracker, style)
}

