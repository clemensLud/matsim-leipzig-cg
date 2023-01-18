package org.matsim.analysis.traffic;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.CrsOptions;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

@CommandLine.Command(
        name = "vehicle-km",
        description = "Analyze road usage by person and freight vehicles"
)
public class VehicleKilometerAnalysis implements MATSimAppCommand {

    @CommandLine.Option(names = "--network", description = "path to scenario network", required = true)
    private static Path network;

    @CommandLine.Option(names = "--plans", description = "path to output plans file", required = true)
    private static Path plans;

    @CommandLine.Option(names = "--subpopulations", description = "List of subpopulations")
    List<String> subpopulations = List.of("businessTraffic_withMC", "businessTraffic_noMC", "person", "freight", "freightTraffic_noMC");

    @CommandLine.Option(names = "--output", description = "output *.csv filepath", defaultValue = "vehicle-kilometers-by-subpopulation.csv")
    private static Path output;

    @CommandLine.Mixin
    ShpOptions shp = new ShpOptions();

    @CommandLine.Mixin
    CrsOptions crs = new CrsOptions();
    private HashMap<String, Double> vehicleKilometeres = new HashMap<>();

    public static void main(String[] args) {
        new VehicleKilometerAnalysis().execute(args);
    }

    @Override
    public Integer call() throws Exception {

        subpopulations.forEach(s -> vehicleKilometeres.put(s, 0.0));

        Map<Id<Link>, ? extends Link> links = NetworkUtils.readNetwork(network.toString()).getLinks();
        Population population = PopulationUtils.readPopulation(plans.toString());

        final Predicate<Link> filter = shp.isDefined() ? link -> {
            Coord from = link.getFromNode().getCoord();
            Coord to = link.getToNode().getCoord();

            ShpOptions.Index index = shp.createIndex(crs.getTargetCRS(), "_");
            return index.contains(from) && index.contains(to);
        } : link -> true;

        for (var person : population.getPersons().values()) {

            if (!isPersonOfInterest(person))
                continue;

            Plan selectedPlan = person.getSelectedPlan();
            String subpopulation = (String) person.getAttributes().getAttribute("subpopulation");

            for (var leg : TripStructureUtils.getLegs(selectedPlan)) {

                String description = leg.getRoute().getRouteDescription();
                Optional<Double> vkm = Arrays.stream(description.split(" "))
                        .map(string -> Id.create(string, Link.class))
                        .map(links::get)
                        .filter(filter)
                        .map(Link::getLength)
                        .reduce(Double::sum);

                vkm.ifPresent(aDouble -> vehicleKilometeres.merge(subpopulation, aDouble / 1000, Double::sum));
            }
        }

        printToFile(vehicleKilometeres, output.toString());

        return 0;
    }

    private boolean isPersonOfInterest(Person person){
        String subpopulation = (String) person.getAttributes().getAttribute("subpopulation");
        return subpopulations.contains(subpopulation);
    }

    private void printToFile(Map<String, Double> map, String filepath) {

        try (PrintWriter pWriter = new PrintWriter(new BufferedWriter(new FileWriter(filepath)))){

            for(Map.Entry<String, Double> entry: map.entrySet()){
                String line = entry.getKey() + ";" + entry.getValue().toString();
                pWriter.println(line);
            }

        } catch (IOException e){
            e.printStackTrace();
        }
    }
}
