package com.warburg.infektion.simulation

import java.util.*

inline class CitizenId(override val id: String) : Id {
    override fun toString(): String = this.id
}

/**
 * @author ewarburg
 */
class Citizen(
    override val id: CitizenId,
    val age: Int,
    val sex: Sex
) : Identifiable<CitizenId> {
    override fun equals(other: Any?): Boolean = other is Citizen && this.id == other.id
    override fun hashCode(): Int = Objects.hashCode(this.id)
    override fun toString(): String = this.id.id
}