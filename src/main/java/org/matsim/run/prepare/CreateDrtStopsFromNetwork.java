package org.matsim.run.prepare;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import picocli.CommandLine;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@CommandLine.Command(
        name = "create-drt-stops",
        description = "create drt stops based on matsim network nodes inside of service area"
)
public class CreateDrtStopsFromNetwork implements MATSimAppCommand {
    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    @CommandLine.Option(names = "--network", description = "network file", required = true)
    private String network;

    @CommandLine.Option(names = "--mode", description = "mode of the drt", required = true)
    private String mode;
    // mode = "drt", "av" or other specific drt operator mode

    @CommandLine.Option(names = "--min-distance", description = "minimal distance between two stops in m", defaultValue = "100.")
    private double minDistance;

    @CommandLine.Option(names = "--output-folder", description = "path to output folder", required = true)
    private String outputFolder;

    private static final Logger log = LogManager.getLogger(CreateDrtStopsFromNetwork.class);

    public static void main(String[] args) throws IOException {
        new CreateDrtStopsFromNetwork().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Config config = ConfigUtils.createConfig();
        config.network().setInputFile(network);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();

        String stopsData = shp.getShapeFile().toString() + "_" + mode + "_stops.csv";
        Geometry drtServiceArea = null;
        Map<Id<Node>, Node> stopNodes = new HashMap<>();

        if(shp.getShapeFile()!=null) {
            drtServiceArea = shp.getGeometry();
        } else {
            log.error("The input shp file is empty or does not exist.");
            return 2;
        }

        for(Node node : network.getNodes().values()) {
            //we dont want pt nodes included as pt has a separate network + no dead ends
            if(MGC.coord2Point(node.getCoord()).within(drtServiceArea) && (node.getInLinks().size() + node.getOutLinks().size() > 2)
            && !node.getId().toString().contains("pt_")) {

                stopNodes.putIfAbsent(node.getId(), node);
            }
        }

        Map<Id<Node>, Node> filteredNodes = filterDistance(minDistance, stopNodes);

        FileWriter csvWriter = new FileWriter(stopsData);
        csvWriter.append("name");
        csvWriter.append(";");
        csvWriter.append("ort");
        csvWriter.append(";");
        csvWriter.append("x");
        csvWriter.append(";");
        csvWriter.append("y");

        for(Id<Node> nodeId : filteredNodes.keySet()) {
            csvWriter.append("\n");
            csvWriter.append(nodeId.toString());
            csvWriter.append(";");
            csvWriter.append("matsimNetworkNode");
            csvWriter.append(";");
            csvWriter.append(Double.toString(filteredNodes.get(nodeId).getCoord().getX()));
            csvWriter.append(";");
            csvWriter.append(Double.toString(filteredNodes.get(nodeId).getCoord().getY()));
        }
        csvWriter.close();

        MATSimAppCommand prepareDrtStops = new PrepareDrtStops();
        prepareDrtStops.execute("--stops-data", stopsData, "--network", config.network().getInputFile(), "--mode", mode,
                "--shp", shp.getShapeFile().toString(), "--output-folder", outputFolder);

        return 0;
    }

    Map<Id<Node>, Node> filterDistance(Double minDistance, Map<Id<Node>, Node> nodes) {
        HashMap<Id<Node>, Node> filteredNodes = new HashMap<>(nodes);

        Network network = NetworkUtils.createNetwork();

        for(Id<Node> nodeId : nodes.keySet()) {
            network.addNode(nodes.get(nodeId));
        }

        for(Id<Node> nodeId : nodes.keySet()) {

            network.removeNode(nodeId);
            Node nearestNode = NetworkUtils.getNearestNode(network, nodes.get(nodeId).getCoord());
            network.addNode(nodes.get(nodeId));

            double distance = CoordUtils.calcEuclideanDistance(nodes.get(nodeId).getCoord(), nearestNode.getCoord());

            if(distance <= minDistance) {
                filteredNodes.remove(nearestNode.getId());
            }
        }
        return filteredNodes;
    }
}