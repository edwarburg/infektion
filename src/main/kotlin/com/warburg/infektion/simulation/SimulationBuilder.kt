package com.warburg.infektion.simulation

import com.warburg.infektion.stats.*
import java.time.LocalTime
import java.util.*

/**
 * @author ewarburg
 */
class PopulationModel(
    val numPeople: Int,
    val ageBins: List<Pair<AgeBracket, PartPer10K>>,
    val numWorkplaces: Int,
    // TODO employmentRate as an age histogram? 18-22 work a less than 30-50 etc
    val employmentRate: RateDistribution,
    val workingAge: HalfOpenRange<Int>,
    val childrenPerCouple: NormalDistribution,
    val personNameGenerator: (Int, Sex, Int) -> String = ::personName,
    val locationNameGenerator: (Int, LocationType) -> String = ::locationName
)

enum class AgeBracket(val ageRange: HalfOpenRange<Int>) {
    Child(     (0  to 13 ).asHalfOpen() ),
    Teenager(  (13 to 20 ).asHalfOpen() ),
    Adult(     (20 to 40 ).asHalfOpen() ),
    MiddleAge( (40 to 65 ).asHalfOpen() ),
    Elderly(   (65 to 100).asHalfOpen() );

    companion object {
        fun forAge(age: Int): AgeBracket = values().firstOrNull { it.ageRange.contains(age) } ?: Elderly
    }
}

fun makeSchedules(
    pop: PopulationModel,
    random: Random
): Map<Citizen, Schedule> {
    val ageDist = HistogramDistribution(pop.ageBins.map { it.first.ageRange to it.second }) { range: HalfOpenRange<Int> -> UniformIntDistribution(range.from, range.to) }
    val citizens = mutableListOf<Citizen>()

    for (i in 0 until pop.numPeople) {
        val age = ageDist.sample(random)
        val sex = if (random.nextBoolean()) Sex.Male else Sex.Female
        val newCitizen = Citizen(
            CitizenId(pop.personNameGenerator(i, sex, age)),
            age,
            sex
        )
        citizens.add(newCitizen)
    }
    val citizensByAgeBracket = citizens.groupBy { AgeBracket.forAge(it.age) }
    val workplaces = Array(pop.numWorkplaces) { i -> Location(LocationId(pop.locationNameGenerator(i, LocationType.Work)), LocationType.Work) }
    val workplacesById = workplaces.associateBy { it.id }
    val workplaceAssignments = mutableMapOf<CitizenId, LocationId>()
    val workplaceDist = UniformIntDistribution(0, pop.numWorkplaces)

    for (bracket in AgeBracket.values()) {
        if (pop.workingAge.overlapsWith(bracket.ageRange)) {
            for (c in citizensByAgeBracket[bracket] ?: emptyList()) {
                if (pop.workingAge.contains(c.age) && pop.employmentRate.sample(random)) {
                    workplaceAssignments[c.id] = workplaces[workplaceDist.sample(random)].id
                }
            }
        }
    }

    // TODO build households more realistically
    val homes = Array(pop.numPeople / 2) { i -> Location(LocationId(pop.locationNameGenerator(i, LocationType.Home)), LocationType.Home) }
    val homesById = homes.associateBy { it.id }
    val homeDist = UniformIntDistribution(0, homes.size)
    val homeAssignments = citizens.associate { it.id to homes[homeDist.sample(random)].id }

    return citizens.associateWith {
        val work = workplaceAssignments[it.id]
        val home = homeAssignments[it.id]!!
        return@associateWith if (work != null) {
            work9to5(homesById.getValue(home), workplacesById.getValue(work))
        } else {
            stayAtHome(homesById.getValue(home))
        }
    }
}

fun loc(id: String, type: LocationType): Location = Location(LocationId(id), type)
fun cit(id: String, age: Int, sex: Sex): Citizen = Citizen(CitizenId(id), age, sex)

private fun personName(i: Int, sex: Sex, age: Int): String = "Person${i}_${sex.toString().first()}$age"
private fun locationName(i: Int, locationType: LocationType): String = "${locationType}_$i"

fun makeSim(
    schedules: Map<Citizen, Schedule>,
    diseaseModel: SIRDiseaseModel,
    initialInfections: Set<CitizenId>,
    random: Random,
    vararg listeners: SimulationListener
): Simulation {
    return Simulation(
        citizens = schedules.keys,
        locations = schedules.values.asSequence().flatMap { it.events.asSequence() }.map { it.location }.toSet(),
        diseaseModel = diseaseModel,
        peopleMover = ScheduleBasedPeopleMover(schedules.mapKeys { it.key.id }),
        initialInfections = initialInfections,
        random = random,
        listeners = listeners.toList()
    )
}

fun schedule(id: String, age: Int, sex: Sex, workplace: Location): Pair<Citizen, Schedule> = cit(id, age, sex) to work9to5(loc("$id's House", LocationType.Home), workplace)

fun work9to5(home: Location, work: Location): Schedule = schedule {
    event { from(LocalTime.of(0, 0)).until(LocalTime.of(9, 0)).at(home) }
    event { from(LocalTime.of(9, 0)).until(LocalTime.of(17, 0)).at(work) }
    event { from(LocalTime.of(17, 0)).until(LocalTime.of(23, 59)).at(home) }
}

private fun stayAtHome(home: Location): Schedule = schedule {
    event { from(LocalTime.of(0, 0)).until(LocalTime.of(23, 59)).at(home) }
}

fun initalInfections(schedules: Set<Citizen>, numInfections: Int, random: Random): Set<CitizenId> = schedules
    .map { it.id }
    .shuffled(random)
    .take(numInfections)
    .toSet()

