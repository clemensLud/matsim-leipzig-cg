package org.matsim.run.prepare;

import org.matsim.contrib.parking.parkingchoice.PC2.infrastructure.PPRestrictedToFacilities;
import org.matsim.contrib.parking.parkingchoice.PC2.infrastructure.PublicParking;
import org.matsim.contrib.parking.parkingchoice.PC2.infrastructure.RentableParking;

import java.util.LinkedList;

public class LeipzigParkings {
    private String parkingLocations;

    public LeipzigParkings(String parkingLocations) {
        this.parkingLocations = parkingLocations;
    }

    //here we need a csv with coordinates, capacity, type (public, rentable etc.), id
    //read it and then filter for type inside of the different getter methods


    public LinkedList<PublicParking> getPublicParkings() {

        LinkedList<PublicParking> publicParkings = new LinkedList<PublicParking>();

        return publicParkings;
    }

    public LinkedList<RentableParking> getRentableParkings() {

        LinkedList<RentableParking> rentableParkings = new LinkedList<RentableParking>();

        return rentableParkings;
    }

    public LinkedList<PPRestrictedToFacilities> getPrivateParkingRestrictedToFacilities() {

        LinkedList<PPRestrictedToFacilities> ppRestrictedToFacilities = new LinkedList<PPRestrictedToFacilities>();

        return ppRestrictedToFacilities;
    }
}
