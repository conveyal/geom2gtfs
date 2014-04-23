package com.conveyal.geom2gtfs;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Frequency;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.serialization.GtfsWriter;
import org.opengis.feature.Feature;
import org.opengis.feature.GeometryAttribute;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.MultiLineString;

public class Main {

	private static final double STOP_SPACING = 400; //meters
	private static final String DEFAULT_AGENCY_ID = "0";
	private static final double VEHICLE_SPEED = 5.3;
	private static final String DEFAULT_NAME = "agency";
	private static final String DEFAULT_AGENCY_URL = "www.example.com";
	private static final String DEFAULT_AGENCY_TIMEZONE = "Europe/London";

	public static void main(String[] args) throws MalformedURLException, IOException {
		if(args.length < 1){
    		System.out.println( "usage: cmd shapefile_fn" );
    		return;
		}
		
		String fn = args[0];
		
		Agency agency = new Agency();
		agency.setId(DEFAULT_AGENCY_ID);
		agency.setName(DEFAULT_NAME);
		agency.setUrl(DEFAULT_AGENCY_URL);
		agency.setTimezone(DEFAULT_AGENCY_TIMEZONE);
		List<Route> routes = new ArrayList<Route>();
		List<Stop> stops = new ArrayList<Stop>();
		List<Trip> trips = new ArrayList<Trip>();
		List<Frequency> frequencies = new ArrayList<Frequency>();
		List<StopTime> stoptimes = new ArrayList<StopTime>();
				
		FeatureSource<?, ?> lines = getFeatureSource(fn);
		
        FeatureCollection<?, ?> featCol = lines.getFeatures();
        FeatureIterator<?> features = featCol.features();
                
        List<ProtoRouteStop> allStops = new ArrayList<ProtoRouteStop>();
        int stopCounter = 0;
        int tripCounter = 0;
        while( features.hasNext() ){
             Feature feat = (Feature) features.next();
             
             System.out.println( "generating stops for \""+feat.getProperty("ROUTE_NAME").getValue() + "\"" );
             
             GeometryAttribute geomAttr = feat.getDefaultGeometryProperty();
             MultiLineString geom = (MultiLineString) geomAttr.getValue();
             
             String routeId = feat.getProperty("ROUTE_ID").getValue().toString();
             String routeName = feat.getProperty("ROUTE_NAME").getValue().toString();
             List<ProtoRouteStop> prss = makeProtoRouteStops( geom, STOP_SPACING, routeId );
             
             // generate route
             Route route = new Route();
             route.setId( new AgencyAndId(DEFAULT_AGENCY_ID, routeId) );
             route.setShortName( routeName );
             route.setAgency(agency);
             routes.add(route);
             
             // generate a trip
             Trip trip = new Trip();
             trip.setRoute(route);
             trip.setId(new AgencyAndId(DEFAULT_AGENCY_ID, String.valueOf(tripCounter)));
             trip.setServiceId(new AgencyAndId(DEFAULT_AGENCY_ID,"0"));
             tripCounter++;
             trips.add(trip);
             
             // generate a frequency
             Frequency freq;
             freq = makeFreq(feat, 6, 9, "FRECHPM", trip);
             frequencies.add( freq );
             freq = makeFreq(feat, 9, 11, "FRECEPM", trip);
             frequencies.add( freq );
             freq = makeFreq(feat, 11, 13, "FRECALM", trip);
             frequencies.add( freq );
             freq = makeFreq(feat, 13, 15, "FRECEPT", trip);
             frequencies.add( freq );
             freq = makeFreq(feat, 15, 18, "FRECHPT", trip);
             frequencies.add( freq );
             
             int i=0;
             for(ProtoRouteStop prs : prss){
	             // generate stops
            	 Stop stop = new Stop();
            	 stop.setLat(prs.coord.y);
            	 stop.setLon(prs.coord.x);
            	 stop.setId( new AgencyAndId(DEFAULT_AGENCY_ID, String.valueOf(stopCounter)) );
            	 stop.setName( String.valueOf(stopCounter) );
            	 stopCounter++;
            	 stops.add(stop);
	             
	             // generate stoptime
            	 StopTime stoptime = new StopTime();
            	 stoptime.setStop(stop);
            	 stoptime.setTrip(trip);
            	 stoptime.setStopSequence(i);
            	 int time = (int)(prs.dist/VEHICLE_SPEED);
            	 stoptime.setArrivalTime(time);
            	 stoptime.setDepartureTime(time);
            	 stoptimes.add(stoptime);
            	 
            	 i++;
             }
             
             allStops.addAll( prss );
             
             System.out.println( "" );

        }
        
        PrintWriter writer = new PrintWriter("out.csv", "UTF-8");
		writer.println("lon,lat,stop_id");
		for( ProtoRouteStop prs : allStops ){
			writer.println( prs.coord.x+","+prs.coord.y+","+prs.routeId );
		}
		writer.close();
		
	    GtfsWriter gtfsWriter = new GtfsWriter();
	    gtfsWriter.setOutputLocation(new File("gtfs_freq.zip"));
	    
	    gtfsWriter.handleEntity( agency );
	    
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
	    
	    gtfsWriter.close();
		
	}

	private static Frequency makeFreq(Feature feat, int beginHour, int endHour, String propName, Trip trip) {
		Frequency freq;
		double perHour;
		double headway;
		
		freq = new Frequency(); // FRECHPM, am peak hour
		freq.setStartTime(beginHour*3600);
		freq.setEndTime(endHour*3600);
		String perHourStr = feat.getProperty(propName).getValue().toString();
		perHour = Double.parseDouble( perHourStr ); // arrivals per hour
		headway = 3600/perHour;
		freq.setHeadwaySecs((int) headway);
		
		freq.setTrip( trip );
		 
		return freq;
	}
	
	private static List<ProtoRouteStop> makeProtoRouteStops(MultiLineString geom, double spacing, String routeId) {
		List<ProtoRouteStop> ret = new ArrayList<ProtoRouteStop>();
		
		Coordinate[] coords = geom.getCoordinates();
		double overshot = 0;
		double segStartDist = 0;
		
		for(int i=0; i<coords.length-1; i++){
			Coordinate p1 = coords[i];
			Coordinate p2 = coords[i+1];
			
			double segCurs = overshot;
			double segLen = greatCircle( p1, p2 );
			
			while( segCurs < segLen ){
				double index = segCurs/segLen;
				Coordinate interp = interpolate( p1, p2, index );
				
				ProtoRouteStop prs = new ProtoRouteStop();
				prs.coord = interp;
				prs.routeId = routeId;
				prs.dist = segStartDist + segCurs;
				ret.add( prs );
				
				segCurs += spacing;
			}
			
			overshot = segCurs - segLen;
			
			segStartDist += segLen;
		}
		
		return ret;
	}

	private static Coordinate interpolate(Coordinate p1, Coordinate p2,
			double index) {
		
		double x = (p2.x-p1.x)*index + p1.x;
		double y = (p2.y-p1.y)*index + p1.y;
		
		return new Coordinate(x,y);
	}

	private static FeatureSource<?, ?> getFeatureSource(String shp_filename)
			throws MalformedURLException, IOException {
		// construct shapefile factory
        File file = new File( shp_filename );
        Map<String,URL> map = new HashMap<String,URL>();
        map.put( "url", file.toURI().toURL() );
        DataStore dataStore = DataStoreFinder.getDataStore( map );
        
        // get shapefile as generic 'feature source'
        String typeName = dataStore.getTypeNames()[0];
        FeatureSource<?, ?> source = dataStore.getFeatureSource( typeName );
		return source;
	}
	
	static double greatCircle(double x1deg, double y1deg, double x2deg, double y2deg){
        double x1 = Math.toRadians(x1deg);
        double y1 = Math.toRadians(y1deg);
        double x2 = Math.toRadians(x2deg);
        double y2 = Math.toRadians(y2deg);
		
		// haverside formula
	    double a = Math.pow(Math.sin((x2-x1)/2), 2)
	             + Math.cos(x1) * Math.cos(x2) * Math.pow(Math.sin((y2-y1)/2), 2);
	
	    // great circle distance in radians
	    double angle2 = 2 * Math.asin(Math.min(1, Math.sqrt(a)));
	
	    // convert back to degrees
	    angle2 = Math.toDegrees(angle2);
	
	    // each degree on a great circle of Earth is 60 nautical miles
	    double distNautMiles = 60 * angle2;
	    
	    double distMeters = distNautMiles * 1852;
	    return distMeters;
	}
	
	static double greatCircle( Coordinate p1, Coordinate p2 ){
		return greatCircle( p1.x, p1.y, p2.x, p2.y );
	}
	
}