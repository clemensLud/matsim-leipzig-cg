package org.matsim.run.prepare;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.parking.parkingchoice.PC2.infrastructure.PC2Parking;
import org.matsim.contrib.parking.parkingchoice.PC2.infrastructure.PPRestrictedToFacilities;
import org.matsim.contrib.parking.parkingchoice.PC2.infrastructure.PublicParking;
import org.matsim.contrib.parking.parkingchoice.PC2.infrastructure.RentableParking;
import org.matsim.contrib.parking.parkingchoice.PC2.scoring.ParkingCostModel;
import org.matsim.facilities.ActivityFacility;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class LeipzigParkings {
    private String parkingLocations;

    public LeipzigParkings(String parkingLocations) {
        this.parkingLocations = parkingLocations;
    }

    //here we need a csv with coordinates, capacity, type (public, rentable etc.), id
    //read it and then filter for type inside of the different getter methods

    List<String[]> readCsv(String path) throws IOException {
        List<String[]> parkingEntries = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        reader.readLine();
        while (true) {
            String parkingEntry = reader.readLine();
            if (parkingEntry == null) {
                break;
            }
            String[] parkingData = parkingEntry.split(";");
            parkingEntries.add(parkingData);
        }
        return parkingEntries;
    }


    public LinkedList<PublicParking> getPublicParkings(String path) throws IOException {

        LinkedList<PublicParking> publicParkings = new LinkedList<PublicParking>();
        PublicParking publicParking = null;

        for(String[] entry : readCsv(path)) {
            if(entry[3].contains("public")) {
                Id<PC2Parking> parkingId = Id.create(entry[0], PC2Parking.class);
                int parkingCapacity = Integer.parseInt(entry[4]);
                Coord parkingCoord = new Coord(Double.parseDouble(entry[1]), Double.parseDouble(entry[2]));
                ParkingCostModel parkingCostModel = null;
                String groupName = "public";

                publicParking = new PublicParking(parkingId, parkingCapacity, parkingCoord, parkingCostModel, groupName);
                publicParkings.add(publicParking);
            }
        }
        return publicParkings;
    }

    public LinkedList<RentableParking> getRentableParkings(String path) throws IOException {

        LinkedList<RentableParking> rentableParkings = new LinkedList<RentableParking>();
        RentableParking rentableParking;

        for(String[] entry : readCsv(path)) {
            if(entry[3].contains("rentable")) {
                Id<PC2Parking> parkingId = Id.create(entry[0], PC2Parking.class);
                int parkingCapacity = Integer.parseInt(entry[4]);
                Coord parkingCoord = new Coord(Double.parseDouble(entry[1]), Double.parseDouble(entry[2]));
                ParkingCostModel parkingCostModel = null;
                String groupName = "rentable";

                rentableParking = new RentableParking(parkingId, parkingCapacity, parkingCoord, parkingCostModel, groupName);
                rentableParkings.add(rentableParking);
            }
        }
        return rentableParkings;
    }

    public LinkedList<PPRestrictedToFacilities> getPrivateParkingRestrictedToFacilities(String path) throws IOException {

        LinkedList<PPRestrictedToFacilities> ppRestrictedToFacilities = new LinkedList<PPRestrictedToFacilities>();
        PPRestrictedToFacilities ppRTF = null;

        for(String[] entry : readCsv(path)) {
            if(entry[3].contains("ppRestrictedToFacilities")) {
                Id<PC2Parking> parkingId = Id.create(entry[0], PC2Parking.class);
                int parkingCapacity = Integer.parseInt(entry[4]);
                Coord parkingCoord = new Coord(Double.parseDouble(entry[1]), Double.parseDouble(entry[2]));
                ParkingCostModel parkingCostModel = null;
                String groupName = "ppRestrictedToFacilities";
                HashSet<Id<ActivityFacility>> facilityIds = null;

                ppRTF = new PPRestrictedToFacilities(parkingId, parkingCapacity, parkingCoord, parkingCostModel, groupName, facilityIds);
                ppRestrictedToFacilities.add(ppRTF);
            }
        }
        return ppRestrictedToFacilities;
    }
}
