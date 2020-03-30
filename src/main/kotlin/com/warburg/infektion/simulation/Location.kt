package com.warburg.infektion.simulation

import java.util.*

inline class LocationId(override val id: String): Id

enum class LocationType {
    Home,
    Work,
    Public
}

// TODO simulate multifamily homes/workplaces with common areas to simulate cross-contamination within multi-location sites?

/**
 * @author ewarburg
 */
class Location(
    override val id: LocationId,
    val type: LocationType = LocationType.Home
) : Identifiable<LocationId> {
    override fun equals(other: Any?): Boolean = other is Location && this.id == other.id
    override fun hashCode(): Int = Objects.hashCode(this.id)
    override fun toString(): String = "${this.id.id} (${this.type})"
}