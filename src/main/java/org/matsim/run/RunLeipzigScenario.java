package org.matsim.run;

import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.analysis.*;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.application.MATSimApplication;
import org.matsim.application.analysis.CheckPopulation;
import org.matsim.application.analysis.population.SubTourAnalysis;
import org.matsim.application.analysis.traffic.LinkStats;
import org.matsim.application.analysis.travelTimeValidation.TravelTimeAnalysis;
import org.matsim.application.options.SampleOptions;
import org.matsim.application.prepare.CreateLandUseShp;
import org.matsim.application.prepare.freight.tripExtraction.ExtractRelevantFreightTrips;
import org.matsim.application.prepare.network.CleanNetwork;
import org.matsim.application.prepare.network.CreateNetworkFromSumo;
import org.matsim.application.prepare.population.*;
import org.matsim.application.prepare.pt.CreateTransitScheduleFromGtfs;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.contrib.bicycle.Bicycles;
import org.matsim.contrib.drt.fare.DrtFareParams;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.DrtConfigs;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtModule;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.contrib.multimodal.router.util.WalkTravelTime;
import org.matsim.contrib.parking.parkingchoice.PC2.GeneralParkingModule;
import org.matsim.contrib.parking.parkingchoice.PC2.infrastructure.PC2Parking;
import org.matsim.contrib.parking.parkingchoice.PC2.infrastructure.PublicParking;
import org.matsim.contrib.parking.parkingchoice.PC2.scoring.ParkingScore;
import org.matsim.contrib.parking.parkingchoice.PC2.scoring.ParkingScoreManager;
import org.matsim.contrib.parking.parkingchoice.PC2.simulation.ParkingInfrastructure;
import org.matsim.contrib.parking.parkingchoice.PC2.simulation.ParkingInfrastructureManager;
import org.matsim.contrib.parking.parkingchoice.example.ParkingBetaExample;
import org.matsim.contrib.parking.parkingchoice.example.ParkingCostCalculatorExample;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.SubtourModeChoiceConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.replanning.choosers.ForceInnovationStrategyChooser;
import org.matsim.core.replanning.choosers.StrategyChooser;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.router.MultimodalLinkChooser;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorConfigGroup;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorsConfigGroup;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorsModule;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.extensions.pt.routing.EnhancedRaptorIntermodalAccessEgress;
import org.matsim.extensions.pt.routing.ptRoutingModes.PtIntermodalRoutingModesConfigGroup;
import org.matsim.extensions.pt.routing.ptRoutingModes.PtIntermodalRoutingModesModule;
import org.matsim.run.prepare.FixNetwork;
import org.matsim.run.prepare.LeipzigParkings;
import org.matsim.run.prepare.PrepareNetwork;
import org.matsim.run.prepare.PreparePopulation;
import picocli.CommandLine;
import playground.vsp.scoring.IncomeDependentUtilityOfMoneyPersonScoringParameters;

import javax.annotation.Nullable;
import java.util.*;

@CommandLine.Command(header = ":: Open Leipzig Scenario ::", version = RunLeipzigScenario.VERSION)
@MATSimApplication.Prepare({
		CreateNetworkFromSumo.class, CreateTransitScheduleFromGtfs.class, TrajectoryToPlans.class, GenerateShortDistanceTrips.class,
		MergePopulations.class, ExtractRelevantFreightTrips.class, DownSamplePopulation.class, PrepareNetwork.class, CleanNetwork.class,
		CreateLandUseShp.class, ResolveGridCoordinates.class, PreparePopulation.class, CleanPopulation.class, AdjustActivityToLinkDistances.class,
		FixSubtourModes.class, FixNetwork.class
})
@MATSimApplication.Analysis({
		CheckPopulation.class, TravelTimeAnalysis.class, LinkStats.class, SubTourAnalysis.class, DrtServiceQualityAnalysis.class, DrtVehiclesRoadUsageAnalysis.class
})
public class RunLeipzigScenario extends MATSimApplication {

	private static final Logger log = LogManager.getLogger(RunLeipzigScenario.class);

	static final String VERSION = "1.1";

	@CommandLine.Mixin
	private final SampleOptions sample = new SampleOptions(1, 10, 25);

	@CommandLine.Option(names = "--with-drt", defaultValue = "false", description = "Enable DRT service")
	private boolean drt;

	@CommandLine.Option(names = "--carfreeArea", defaultValue = "false", description = "enable simulation of carfree area")
	private boolean carfreeArea;

	@CommandLine.Option(names = "--bikes", defaultValue = "true", description = "Enable qsim for bikes", negatable = true)
	private boolean bike;

	@CommandLine.Option(names = "--income-dependent", defaultValue = "true", description = "Income dependent scoring", negatable = true)
	private boolean incomeDependent;

	@CommandLine.Option(names = "--parking", description = "path to parking locations csv file")
	private String parking;

	public RunLeipzigScenario(@Nullable Config config) {
		super(config);
	}

	public RunLeipzigScenario() {
		super(String.format("scenarios/input/leipzig-v%s-25pct.config.xml", VERSION));
	}

	public static void main(String[] args) {
		MATSimApplication.run(RunLeipzigScenario.class, args);
	}

	@Nullable
	@Override
	protected Config prepareConfig(Config config) {

		for (long ii = 600; ii <= 97200; ii += 600) {

			for (String act : List.of("home", "restaurant", "other", "visit", "errands",
					"educ_higher", "educ_secondary", "educ_primary", "educ_tertiary", "educ_kiga", "educ_other")) {
				config.planCalcScore()
						.addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams(act + "_" + ii).setTypicalDuration(ii));
			}

			config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("work_" + ii).setTypicalDuration(ii)
					.setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
			config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("business_" + ii).setTypicalDuration(ii)
					.setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
			config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("leisure_" + ii).setTypicalDuration(ii)
					.setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));

			config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("shop_daily_" + ii).setTypicalDuration(ii)
					.setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
			config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("shop_other_" + ii).setTypicalDuration(ii)
					.setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
		}

		config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("car interaction").setTypicalDuration(60));
		config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("other").setTypicalDuration(600 * 3));

		config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("freight_start").setTypicalDuration(60 * 15));
		config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("freight_end").setTypicalDuration(60 * 15));

		if (sample.isSet()) {
			config.controler().setOutputDirectory(sample.adjustName(config.controler().getOutputDirectory()));
			config.controler().setRunId(sample.adjustName(config.controler().getRunId()));
			config.plans().setInputFile(sample.adjustName(config.plans().getInputFile()));

			config.qsim().setFlowCapFactor(sample.getSize() / 100.0);
			config.qsim().setStorageCapFactor(sample.getSize() / 100.0);
		}

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.info);
		config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);

		if (drt) {
			MultiModeDrtConfigGroup multiModeDrtConfigGroup = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
			ConfigUtils.addOrGetModule(config, DvrpConfigGroup.class);
			DrtConfigs.adjustMultiModeDrtConfig(multiModeDrtConfigGroup, config.planCalcScore(), config.plansCalcRoute());
		}

		if (bike) {

			log.info("Simulating with bikes on the network");

			BicycleConfigGroup bikeConfigGroup = ConfigUtils.addOrGetModule(config, BicycleConfigGroup.class);
			bikeConfigGroup.setBicycleMode(TransportMode.bike);

			config.qsim().setUsingTravelTimeCheckInTeleportation(true);
			config.qsim().setUsePersonIdForMissingVehicleId(false);

			Set<String> modes = Sets.newHashSet(TransportMode.bike);
			modes.addAll(config.qsim().getMainModes());

			config.qsim().setMainModes(modes);
			config.qsim().setLinkDynamics(QSimConfigGroup.LinkDynamics.PassingQ);

		} else
			log.warn("Bikes on network are disabled");


		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {


		for (Link link : scenario.getNetwork().getLinks().values()) {
			Set<String> modes = link.getAllowedModes();

			// allow freight traffic together with cars
			if (modes.contains("car")) {
				HashSet<String> newModes = Sets.newHashSet(modes);
				newModes.add("freight");

				link.setAllowedModes(newModes);
			}
		}

		if (drt) {
			scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DrtRoute.class, new DrtRouteFactory());
		}
	}

	@Override
	protected void prepareControler(Controler controler) {
		Config config = controler.getConfig();

		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				install(new LeipzigPtFareModule());
				install(new SwissRailRaptorModule());

				addTravelTimeBinding(TransportMode.ride).to(networkTravelTime());
				addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());

				if (incomeDependent) {
					bind(ScoringParametersForPerson.class).to(IncomeDependentUtilityOfMoneyPersonScoringParameters.class).asEagerSingleton();
				}

				bind(AnalysisMainModeIdentifier.class).to(LeipzigMainModeIdentifier.class);
				addControlerListenerBinding().to(ModeChoiceCoverageControlerListener.class);

				if(carfreeArea) {
					bind(MultimodalLinkChooser.class).to(CarfreeMultimodalLinkChooser.class);
				}

				addControlerListenerBinding().to(StrategyWeightFadeout.class).in(Singleton.class);

				Multibinder<StrategyWeightFadeout.Schedule> schedules = StrategyWeightFadeout.getBinder(binder());

				schedules.addBinding().toInstance(new StrategyWeightFadeout.Schedule(DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice, "person", 0.65, 0.85));
				schedules.addBinding().toInstance(new StrategyWeightFadeout.Schedule(DefaultPlanStrategiesModule.DefaultStrategy.ChangeSingleTripMode, "person", 0.65, 0.85));
				schedules.addBinding().toInstance(new StrategyWeightFadeout.Schedule(DefaultPlanStrategiesModule.DefaultStrategy.ChangeTripMode, "person", 0.65, 0.85));
				schedules.addBinding().toInstance(new StrategyWeightFadeout.Schedule(DefaultPlanStrategiesModule.DefaultStrategy.ReRoute, "person", 0.78));

				bind(new TypeLiteral<StrategyChooser<Plan, Person>>() {}).toInstance(new ForceInnovationStrategyChooser<>(10, ForceInnovationStrategyChooser.Permute.yes));
			}
		});

		if (drt) {
			MultiModeDrtConfigGroup multiModeDrtConfigGroup = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);

            //set fare params; flexa has the same prices as leipzig PT: Values taken out of LeipzigPtFareModule -sm0522
            Double ptBaseFare = 2.4710702921120262;
            Double ptDistanceFare = 0.00017987993018495408;

            DrtFareParams drtFareParams = new DrtFareParams();
            drtFareParams.setBaseFare(ptBaseFare);
            drtFareParams.setDistanceFare_m(ptDistanceFare);
            drtFareParams.setTimeFare_h(0.);
            drtFareParams.setDailySubscriptionFee(0.);

            Set<String> drtModes = new HashSet<>();

            multiModeDrtConfigGroup.getModalElements().forEach(drtConfigGroup -> {
                drtConfigGroup.addParameterSet(drtFareParams);
                DrtConfigs.adjustDrtConfig(drtConfigGroup, config.planCalcScore(), config.plansCalcRoute());
                drtModes.add(drtConfigGroup.getMode());
            });

			controler.addOverridingModule(new DvrpModule());
			controler.addOverridingModule(new MultiModeDrtModule());
			controler.configureQSimComponents(DvrpQSimComponents.activateAllModes(multiModeDrtConfigGroup));

			prepareDrtFareCompensation(config, controler, drtModes, ptBaseFare);
		}

		if (bike) {
			Bicycles.addAsOverridingModule(controler);
		}

		if (parking!=null) {
			//Here a new version of the parkingScoreManager could be implemented -sm0822
			ParkingScore leipzigParkingScoreManager = new ParkingScoreManager(new WalkTravelTime(controler.getConfig().plansCalcRoute()), controler.getScenario());
			//TODO research fitting value
			leipzigParkingScoreManager.setParkingScoreScalingFactor(1);
			//TODO research fitting value + implement own ParkingBetaClass
			leipzigParkingScoreManager.setParkingBetas(new ParkingBetaExample());

			//the following is just copied from RunParkingChoiceExample.class
			//TODO define own list of public parkings, outsource it to separate class
			//something like this:
//			LeipzigParkings leipzigParkings = new LeipzigParkings();
//			LinkedList<PublicParking> publicParkings = leipzigParkings.getPublicParkings();

			ParkingInfrastructure leipzigParkingInfrastructureManager = new ParkingInfrastructureManager(leipzigParkingScoreManager, controler.getEvents());
			{
				//TODO out this into test csv file
				LinkedList<PublicParking> publicParkings = new LinkedList<PublicParking>();
				//parking 1: we place this near the workplace
				publicParkings.add(new PublicParking(Id.create("workPark", PC2Parking.class), 98, new Coord((double) 10000, (double) 0),
						new ParkingCostCalculatorExample(1), "park"));
				//parking 2: we place this at home
				final double x = -25000;
				publicParkings.add(new PublicParking(Id.create("homePark", PC2Parking.class), 98, new Coord(x, (double) 0),
						new ParkingCostCalculatorExample(0), "park"));
				leipzigParkingInfrastructureManager.setPublicParkings(publicParkings);
			}

			GeneralParkingModule parkingModule = new GeneralParkingModule(controler);
			parkingModule.setParkingScoreManager(leipzigParkingScoreManager);
			parkingModule.setParkingInfrastructurManager(leipzigParkingInfrastructureManager);
		}
	}

    protected void prepareDrtFareCompensation(Config config, Controler controler, Set<String> nonPtModes, Double ptBaseFare) {
        IntermodalTripFareCompensatorsConfigGroup intermodalTripFareCompensatorsConfigGroup =
                ConfigUtils.addOrGetModule(config, IntermodalTripFareCompensatorsConfigGroup.class);

        IntermodalTripFareCompensatorConfigGroup drtFareCompensator = new IntermodalTripFareCompensatorConfigGroup();
        drtFareCompensator.setCompensationCondition(IntermodalTripFareCompensatorConfigGroup.CompensationCondition.PtModeUsedAnywhereInTheDay);

        //Flexa is integrated into pt system, so users only pay once
        drtFareCompensator.setCompensationMoneyPerTrip(ptBaseFare);
        drtFareCompensator.setNonPtModes(ImmutableSet.copyOf(nonPtModes));

        intermodalTripFareCompensatorsConfigGroup.addParameterSet(drtFareCompensator);
        controler.addOverridingModule(new IntermodalTripFareCompensatorsModule());

        //for intermodality between pt and drt the following modules have to be installed and configured
        String artificialPtMode = "pt_w_drt_allowed";
        PtIntermodalRoutingModesConfigGroup ptIntermodalRoutingModesConfig = ConfigUtils.addOrGetModule(config, PtIntermodalRoutingModesConfigGroup.class);
        PtIntermodalRoutingModesConfigGroup.PtIntermodalRoutingModeParameterSet ptIntermodalRoutingModesParamSet
                = new PtIntermodalRoutingModesConfigGroup.PtIntermodalRoutingModeParameterSet();

        ptIntermodalRoutingModesParamSet.setDelegateMode(TransportMode.pt);
        ptIntermodalRoutingModesParamSet.setRoutingMode(artificialPtMode);

        PtIntermodalRoutingModesConfigGroup.PersonAttribute2ValuePair personAttrParamSet
                = new PtIntermodalRoutingModesConfigGroup.PersonAttribute2ValuePair();
        personAttrParamSet.setPersonFilterAttribute("canUseDrt");
        personAttrParamSet.setPersonFilterValue("true");
        ptIntermodalRoutingModesParamSet.addPersonAttribute2ValuePair(personAttrParamSet);

        ptIntermodalRoutingModesConfig.addParameterSet(ptIntermodalRoutingModesParamSet);

        controler.addOverridingModule(new PtIntermodalRoutingModesModule());

        //SRRConfigGroup needs to have the same personFilterAttr and Value as PtIntermodalRoutingModesConfigGroup
        SwissRailRaptorConfigGroup ptConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
        for( SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet paramSet : ptConfig.getIntermodalAccessEgressParameterSets() ) {
            if(paramSet.getMode().contains("drt")) {
                paramSet.setPersonFilterAttribute("canUseDrt");
                paramSet.setPersonFilterValue("true");
            }
        }

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                bind(RaptorIntermodalAccessEgress.class).to(EnhancedRaptorIntermodalAccessEgress.class);
            }
        });

        //finally the new pt mode has to be added to subtourModeChoice
        SubtourModeChoiceConfigGroup modeChoiceConfigGroup = ConfigUtils.addOrGetModule(config, SubtourModeChoiceConfigGroup.class);
		List<String> modes = new ArrayList<>();
		Collections.addAll(modes, modeChoiceConfigGroup.getModes());
        modes.add(artificialPtMode);
        modeChoiceConfigGroup.setModes(modes.toArray(new String[modes.size()]));
    }
}
