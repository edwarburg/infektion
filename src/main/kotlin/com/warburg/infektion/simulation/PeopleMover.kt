package com.warburg.infektion.simulation

/**
 * @author ewarburg
 */
interface PeopleMover {
    fun move(citizen: Citizen, currentLocation: Location?, tick: Tick): Location?
}
