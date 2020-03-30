# infektion
## Epidemic simulator

What better way for a programmer to emotionally process a pandemic and gain a feeling of control over something they're helpless to defend against than to build a simulation where they get to control everything?

This attempts to simulate the spread of a disease among a population, given certain characteristics of the disease such as its transmission rate, lethality rate, length of infection, etc, and given a population that moves from location to location.

# Simulation
During each tick of the simulation, a few things may happen:
## Relocation
People may move from location to location, according to their set schedule. For example, a person may start the day at home, before leaving for work at 9, and returning home at 5
## Infection
People in the same location at the same time interact with one another, and if one of them is infected, has a chance to infect the other.
## Death
People who are infected may die from the disease. The fatality rate varies by how long the person has had the disease, and the person's age.
## Recovery
People who are infected but survive long enough eventually recover. Infected people may not be reinfected by the disease.

## Future simulation ideas
- Vary rate of transmission by phase of disease. For example, some diseases are much more transmissible when the patient is showing symptoms than when they are asymptomatic.
- Vaccinate an initial portion of the population
- Vary schedule by infection state - most infected people should be smart enough to stay at home, at least once they're symptomatic.
- Build more realistic households with similarly aged partners and appropriately aged children
- Model schools for children to go to, and common areas like grocery stores or concert venues for disease to potentially spread
- Have people go between regions to visit family, etc.
- More accurately model transmission within a location, rather than just having everyone interact with everyone
- Model various mitigation techniques like social distancing, quarantining, contact tracing, closing borders, etc
- Other disease models: https://en.wikipedia.org/wiki/Compartmental_models_in_epidemiology



# Visualization
At the moment, output is logged two ways: a simple text log, and a graphviz dotfile representing who was infected.
## Future visualization ideas
- Breakdown of infected vs susceptible vs recovered over time (the curve of "flatten the curve")
