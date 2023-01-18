package org.matsim.analysis.traffic;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
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
import org.matsim.core.population.algorithms.ParallelPersonAlgorithmUtils;
import org.matsim.core.population.algorithms.PersonAlgorithm;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.TripStructureUtils;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Predicate;

@CommandLine.Command(
		name = "vehicle-km",
		description = "Analyze road usage by person and freight vehicles"
)
public class VehicleKilometerAnalysis implements MATSimAppCommand, PersonAlgorithm {

	private static final Set<String> IGNORED_MODES = Set.of(TransportMode.walk, TransportMode.bike, TransportMode.pt, TransportMode.drt);

	private static final Logger log = LogManager.getLogger(VehicleKilometerAnalysis.class);

	@CommandLine.Option(names = "--network", description = "path to scenario network", required = true)
	private static Path network;

	@CommandLine.Option(names = "--plans", description = "path to output plans file", required = true)
	private static Path plans;

	@CommandLine.Option(names = "--subpopulations", description = "List of subpopulations")
	private List<String> subpopulations = List.of("businessTraffic_withMC", "businessTraffic_noMC", "person", "freight", "freightTraffic_noMC");

	@CommandLine.Option(names = "--output", description = "output *.csv filepath", defaultValue = "vehicle-kilometers-by-subpopulation.csv")
	private static Path output;

	@CommandLine.Mixin
	private ShpOptions shp = new ShpOptions();

	@CommandLine.Mixin
	private CrsOptions crs = new CrsOptions();

	private final Map<String, DoubleAdder> vehicleKilometeres = new ConcurrentHashMap<>();

	private Predicate<Link> filter;
	private final AtomicInteger counter = new AtomicInteger(0);

	private Map<Id<Link>, ? extends Link> links;

	public static void main(String[] args) {
		new VehicleKilometerAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		links = NetworkUtils.readNetwork(network.toString()).getLinks();
		Population population = PopulationUtils.readPopulation(plans.toString());

		if (shp.isDefined()) {
			ShpOptions.Index index = shp.createIndex(crs.getTargetCRS(), "_");

			filter = link -> {
				Coord from = link.getFromNode().getCoord();
				Coord to = link.getToNode().getCoord();
				return index.contains(from) && index.contains(to);
			};
		} else {
			filter = link -> true;
		}


		log.info("Begin population analysis.");

		ParallelPersonAlgorithmUtils.run(population, Runtime.getRuntime().availableProcessors(), this);


		log.info("Write results to {}", output.toString());

		printToFile(vehicleKilometeres, output.toString());

		return 0;
	}

	@Override
	public void run(Person person) {


		if (!isPersonOfInterest(person))
			return;

		Plan selectedPlan = person.getSelectedPlan();
		String subpopulation = (String) person.getAttributes().getAttribute("subpopulation");

		// maps leg mode to distances
		Object2DoubleMap<String> sums = new Object2DoubleOpenHashMap<>();

		for (var leg : TripStructureUtils.getLegs(selectedPlan)) {

			if (IGNORED_MODES.contains(leg.getMode()))
				continue;

			if (!(leg.getRoute() instanceof NetworkRoute nr))
				continue;

			for (Id<Link> id : nr.getLinkIds()) {

				Link link = links.get(id);
				if (link == null || !filter.test(link))
					continue;

				sums.merge(leg.getMode(), link.getLength(), Double::sum);
			}

			if (counter.incrementAndGet() % 10000 == 0)
				log.info("Processed trips: {}", counter);

		}


		for (Object2DoubleMap.Entry<String> e : sums.object2DoubleEntrySet()) {
			vehicleKilometeres.computeIfAbsent(subpopulation + ";" + e.getKey(), k -> new DoubleAdder()).add(e.getDoubleValue());
		}

	}

	private boolean isPersonOfInterest(Person person) {
		String subpopulation = (String) person.getAttributes().getAttribute("subpopulation");
		return subpopulations.contains(subpopulation);
	}

	private void printToFile(Map<String, DoubleAdder> map, String filepath) {

		// TODO: use CSVPrinter

		try (PrintWriter pWriter = new PrintWriter(new BufferedWriter(new FileWriter(filepath)))) {

			for (Map.Entry<String, DoubleAdder> entry : map.entrySet()) {
				String line = entry.getKey() + ";" + entry.getValue().doubleValue();
				pWriter.println(line);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
