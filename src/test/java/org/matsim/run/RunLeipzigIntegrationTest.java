package org.matsim.run;

import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.matsim.application.MATSimApplication;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.testcases.MatsimTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class RunLeipzigIntegrationTest {
	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	private static final String URL = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/leipzig/leipzig-v1.1/input/";

	@Test
	public void runPoint1pct() {

		Path output = Path.of("output/it-1pct");

		Config config = ConfigUtils.loadConfig("scenarios/input/leipzig-v1.1-25pct.config.xml");

		config.global().setNumberOfThreads(1);
		config.qsim().setNumberOfThreads(1);

		// Change input paths
		config.plans().setInputFile(URL + "leipzig-v1.1-0.1pct.plans.xml.gz");
		config.transit().setTransitScheduleFile(URL + config.transit().getTransitScheduleFile());
		config.transit().setVehiclesFile(URL + config.transit().getVehiclesFile());
		config.network().setInputFile(URL + config.network().getInputFile());

		Controler controler = RunLeipzigScenario.prepare(RunLeipzigScenario.class, config,
				"run"
		);

		config.controler().setLastIteration(1);
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setOutputDirectory(output.toString());

		controler.run();

		assertThat(output)
				.exists()
				.isNotEmptyDirectory();

	}

	@Test
	public final void runDrtExamplePopulationTest() {
//		Config config = ConfigUtils.loadConfig("Y:/matsim-leipzig/scenarios/input/leipzig-v1.0-test.with-drt.config.xml");
		Config config = ConfigUtils.loadConfig("scenarios/input/leipzig-v1.1-test.with-drt.config.xml");

		config.global().setNumberOfThreads(1);
		config.qsim().setNumberOfThreads(1);
		config.controler().setLastIteration(1);
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.network().setInputFile(URL + "leipzig-v1.1-network-with-pt-drt.xml.gz");
		config.plans().setInputFile(URL + "leipzig-v1.1-0.1pct.plans.xml.gz");
		config.transit().setTransitScheduleFile(URL + "leipzig-v1.1-transitSchedule.xml.gz");
		config.transit().setVehiclesFile(URL + "leipzig-v1.1-transitVehicles.xml.gz");

//		DeleteRoutes deleteRoutes = new DeleteRoutes(config);
//		Config newConfig = deleteRoutes.deleteRoutesFromPlans(config);

		MATSimApplication.execute(RunLeipzigScenario.class, config, "run", "--1pct", "--with-drt");
//		MATSimApplication.execute(RunLeipzigScenario.class, newConfig, "run", "--1pct", "--with-drt");
	}

	@Test
	public final void runParkingTest() {
		//TODO
	}
}
