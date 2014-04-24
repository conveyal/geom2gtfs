package com.conveyal.geom2gtfs;

import java.util.ArrayList;
import java.util.List;

import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.Frequency;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.serialization.GtfsWriter;

public class GtfsQueue {
	public List<Agency> agencies = new ArrayList<Agency>();
	public List<Route> routes = new ArrayList<Route>();
	public List<Stop> stops = new ArrayList<Stop>();
	public List<Trip> trips = new ArrayList<Trip>();
	public List<Frequency> frequencies = new ArrayList<Frequency>();
	public List<StopTime> stoptimes = new ArrayList<StopTime>();
	public List<ServiceCalendar> calendars = new ArrayList<ServiceCalendar>();
	
	public void dumpToWriter(GtfsWriter gtfsWriter) {
	    for(Agency agency : agencies){
	    	gtfsWriter.handleEntity( agency );
	    }
	    for(Route route : routes){
	    	gtfsWriter.handleEntity(route);
	    }
	    for(Trip trip : trips){
	    	gtfsWriter.handleEntity(trip);
	    }
	    for(Stop stop : stops){
	    	gtfsWriter.handleEntity(stop);
	    }
	    for(StopTime stoptime : stoptimes){
	    	gtfsWriter.handleEntity(stoptime);
	    }
	    for(Frequency fr : frequencies){
	    	gtfsWriter.handleEntity(fr);
	    }
	    for(ServiceCalendar sc : calendars){
	    	gtfsWriter.handleEntity(sc);
	    }
	}

}
