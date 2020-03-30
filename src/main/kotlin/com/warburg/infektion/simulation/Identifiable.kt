package com.warburg.infektion.simulation

interface Id {
    val id: String
}

/**
 * @author ewarburg
 */
interface Identifiable<T : Id> {
    val id: T
}