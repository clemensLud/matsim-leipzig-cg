package org.matsim.analysis.traffic;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
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

    private final HashMap<String, Double> vehicleKilometeres = new HashMap<>();

    private static final Logger log = LogManager.getLogger(VehicleKilometerAnalysis.class);

    public static void main(String[] args) {
        new VehicleKilometerAnalysis().execute(args);
    }

    @Override
    public Integer call() throws Exception {

        subpopulations.forEach(s -> vehicleKilometeres.put(s, 0.0));
        List<String> ignoredModes = List.of(TransportMode.walk, TransportMode.bike, TransportMode.pt, TransportMode.drt);
        Map<Id<Link>, ? extends Link> links = NetworkUtils.readNetwork(network.toString()).getLinks();
        Population population = PopulationUtils.readPopulation(plans.toString());

        final Predicate<Link> filter;
        if(shp.isDefined()){
            ShpOptions.Index index = shp.createIndex(crs.getTargetCRS(), "_");

            filter = link -> {
                if (link == null)
                    return false;

                Coord from = link.getFromNode().getCoord();
                Coord to = link.getToNode().getCoord();
                return index.contains(from) && index.contains(to);
            };
        } else {
            filter = link -> true;
        }

        int counter = 0;

        log.info("Begin population analysis.");
        for (var person : population.getPersons().values()) {

            if (!isPersonOfInterest(person))
                continue;

            Plan selectedPlan = person.getSelectedPlan();
            String subpopulation = (String) person.getAttributes().getAttribute("subpopulation");

            for (var leg : TripStructureUtils.getLegs(selectedPlan)) {

                if(ignoredModes.contains(leg.getMode()))
                    continue;

                String description = leg.getRoute().getRouteDescription();
                Optional<Double> vkm = Arrays.stream(description.split(" "))
                        .map(string -> Id.create(string, Link.class))
                        .map(id -> {
                            Link link = links.get(id);
                            if(link == null)
                                log.info("Can't find a link for id {}", id.toString());
                            return link;
                        })
                        .filter(filter)
                        .map(Link::getLength)
                        .reduce(Double::sum);

                if(counter++ % 10000 == 0)
                    log.info("Processed trips: {}", counter);

                vkm.ifPresent(aDouble -> vehicleKilometeres.merge(subpopulation, aDouble / 1000, Double::sum));
            }
        }

        log.info("Write results to {}", output.toString());
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
