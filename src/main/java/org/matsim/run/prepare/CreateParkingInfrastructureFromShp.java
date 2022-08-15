package org.matsim.run.prepare;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.opengis.feature.simple.SimpleFeature;
import picocli.CommandLine;

import java.io.FileWriter;
import java.util.Random;

@CommandLine.Command(
        name = "create-parking-infrastructure",
        description = "Write parking csv"
)

public class CreateParkingInfrastructureFromShp implements MATSimAppCommand {
    @CommandLine.Mixin
    private ShpOptions shp = new ShpOptions();

    @CommandLine.Option(names = "--network", description = "network file", required = true)
    private String network;

    @CommandLine.Option(names = "--parking-type", description = "wished type for parking spots. Choose from public, rentable or ppRestrictedToFacilities", required = true)
    private String parkingType;

    @CommandLine.Option(names = "--output", description = "path to output file", required = true)
    private String outputFile;

    public static void main(String[] args)  {
        new CreateParkingInfrastructureFromShp().execute(args);
    }

    @Override
    public Integer call() throws Exception {
        Config config = ConfigUtils.createConfig();
        config.network().setInputFile(network);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = scenario.getNetwork();

        Geometry allAreas = shp.getGeometry();
        Geometry area = null;

        Network networkOutsideOfShape = networkOutsideOfShapeFilter(network, allAreas);
        Random rnd = new Random();

        FileWriter csvWriter = new FileWriter(outputFile);
        csvWriter.append("parkingID");
        csvWriter.append(";");
        csvWriter.append("x");
        csvWriter.append(";");
        csvWriter.append("y");
        csvWriter.append(";");
        csvWriter.append("type");
        csvWriter.append(";");
        csvWriter.append("capacity");

        for(SimpleFeature feature : shp.readFeatures()) {
            area = (Geometry) feature.getDefaultGeometry();
            Coord coord = new Coord(area.getCentroid().getX(), area.getCentroid().getY());

            Link parkingLink = NetworkUtils.getNearestLink(networkOutsideOfShape, coord);

            csvWriter.append("\n");
            csvWriter.append(parkingLink.getFromNode().getId().toString());
            csvWriter.append(";");
            csvWriter.append(Double.toString(parkingLink.getFromNode().getCoord().getX()));
            csvWriter.append(";");
            csvWriter.append(Double.toString(parkingLink.getFromNode().getCoord().getY()));
            csvWriter.append(";");
            csvWriter.append(parkingType);
            csvWriter.append(";");
            //TODO find fitting distribution / value for cap here
            csvWriter.append(Integer.toString(rnd.nextInt(200)));
        }
        csvWriter.close();

        return 0;
    }

    Network networkOutsideOfShapeFilter(Network network, Geometry areaToFilter) {

        Network filteredNetwork = NetworkUtils.createNetwork();
        GeometryFactory gf = new GeometryFactory();
        LineString line = null;

        for(Link link : network.getLinks().values()) {
            if(!link.getAllowedModes().contains(TransportMode.car)) {
                continue;
            }

            line = gf.createLineString(new Coordinate[]{
                    MGC.coord2Coordinate(link.getFromNode().getCoord()),
                    MGC.coord2Coordinate(link.getToNode().getCoord())
            });
            
            if(!line.intersects(areaToFilter)) {

                if(!filteredNetwork.getLinks().containsKey(link.getId())) {
                    if(!filteredNetwork.getNodes().containsKey(link.getFromNode().getId())) {
                        filteredNetwork.addNode(link.getFromNode());
                    }

                    if(!filteredNetwork.getNodes().containsKey(link.getToNode().getId())) {
                        filteredNetwork.addNode(link.getToNode());
                    }

                    if(!filteredNetwork.getLinks().containsKey(link.getId())) {
                        filteredNetwork.addLink(link);
                    }
                }
            }
        }

        NetworkUtils.writeNetwork(filteredNetwork, "C:/Users/Simon/Desktop/2022-08-11_parkingDataCreation/filteredNetwork.xml.gz");
        return filteredNetwork;
    }
}
