package com.conveyal.geom2gtfs;

import com.vividsolutions.jts.geom.Geometry;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.onebusaway.gtfs.model.*;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.serialization.GtfsWriter;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;


public class Main {

	static final String DEFAULT_AGENCY_ID = "0";
	private static final String DEFAULT_CAL_ID = "0";

	private Config config;
	
	// used to generate ids and names for routes that do not have them
	private int nextId = 0;

	private GtfsQueue queue = null;

	/**
	 * wrapper main function creates instance so that calling geom2gtfs twice in the same JVM doesn't cause conflicts
	 * (this can happen in analyst-server, see conveyal/analyst-server#262)
     */
	public static void main (String[] args) throws Exception {
		if (args.length < 3) {
			System.out.println("usage: cmd shapefile_fn config_fn output_fn");
			return;
		}
		new Main().main(args[0], args[1], args[2]);
	}

	public void main(String fn, String config_fn, String output_fn) throws Exception {
		config = new Config(config_fn);

		// check if config specifies a csv join
		CsvJoinTable csvJoin = config.getCsvJoin();

		queue = new GtfsQueue();

		ServiceCalendar cal = new ServiceCalendar();
		cal.setMonday(1);
		cal.setTuesday(1);
		cal.setWednesday(1);
		cal.setThursday(1);
		cal.setFriday(1);
		cal.setSaturday(1);
		cal.setSunday(1);
		cal.setStartDate(new ServiceDate(config.getStartDate()));
		cal.setEndDate(new ServiceDate(config.getEndDate()));
		cal.setServiceId(new AgencyAndId(DEFAULT_AGENCY_ID, DEFAULT_CAL_ID));
		queue.calendars.add(cal);

		Agency agency = new Agency();
		agency.setId(DEFAULT_AGENCY_ID);
		agency.setName(config.getAgencyName());
		agency.setUrl(config.getAgencyUrl());
		agency.setTimezone(config.getAgencyTimezone());
		queue.agencies.add(agency);

		List<Feature> features = getFeatures(fn);
		
		List<ExtendedFeature> extFeatures = joinFeatures( features, csvJoin );
		
		extFeatures = filterFeatures( extFeatures );
		
		Map<String, List<ExtendedFeature>> featureGroups = groupFeatures( extFeatures );
		
		StopGenerator stopGenerator = config.getStopGenerator();
		
		for( Entry<String, List<ExtendedFeature>> group : featureGroups.entrySet() ){		    
			featToGtfs(group.getValue(), agency, stopGenerator, group.getKey());
		}

		System.out.println( "writing to "+output_fn );
		GtfsWriter gtfsWriter = new GtfsWriter();
		gtfsWriter.setOutputLocation(new File(output_fn));
		queue.dumpToWriter(gtfsWriter);
		gtfsWriter.close();
		System.out.println( "done" );
	}

	private Map<String, List<ExtendedFeature>> groupFeatures(List<ExtendedFeature> extFeatures) {
		Map<String, List<ExtendedFeature>> ret = new HashMap<String, List<ExtendedFeature>>();
		
		// gather by route id
		for( ExtendedFeature exft : extFeatures ){
			String id = exft.getProperty( config.getRouteIdPropName() );
			
			if (id == null) {
			    id = "generated_" + nextId++;
			}
			    
			
			List<ExtendedFeature> group = ret.get(id);
			if(group==null){
				group = new ArrayList<ExtendedFeature>();
				ret.put(id, group);
			}
			
			group.add(exft);
		}
		
		// order each group by segment
		for( List<ExtendedFeature> group : ret.values() ){
			Collections.sort(group, new Comparator<ExtendedFeature>(){

				@Override
				public int compare(ExtendedFeature o1, ExtendedFeature o2) {
					String segStr1 = o1.getProperty("segment");
					Integer seg1;
					try{
						seg1 = Integer.parseInt(segStr1);
					} catch (NumberFormatException ex){
						seg1 = 0;
					}
					
					String segStr2 = o2.getProperty("segment");
					Integer seg2;
					try{
						seg2 = Integer.parseInt(segStr2);
					} catch (NumberFormatException ex){
						seg2 = 0;
					}
					
					return seg1-seg2;
				}
				
			});
		}
		
		return ret;
	}

	private List<ExtendedFeature> filterFeatures(List<ExtendedFeature> extFeatures) {
		List<ExtendedFeature> ret = new ArrayList<ExtendedFeature>();
		
		for( ExtendedFeature exft : extFeatures ){
			if (config.passesFilter(exft)) {
				ret.add(exft);
			}
		}
		
		return ret;
	}

	private List<ExtendedFeature> joinFeatures(List<Feature> features, CsvJoinTable csvJoin) {
		List<ExtendedFeature> ret = new ArrayList<ExtendedFeature>();
		
		for (Feature feat : features) {

			ExtendedFeature exft = new ExtendedFeature(feat, csvJoin);
			
			ret.add( exft );
		}
		
		return ret;
	}

	private void featToGtfs(List<ExtendedFeature> group, Agency agency,
	        StopGenerator stopGenerator, String routeId) throws Exception {
		
		ExtendedFeature exemplar = group.get(0);

		// get route type
		Integer mode = config.getMode(exemplar);

		// generate route
		String routeName = exemplar.getProperty(config.getRouteNamePropName());
		
		if (routeName == null)
		    routeName = routeId;
		    
                System.out.println("generating elements for \"" + routeName + "\"");
		
		Route route = new Route();
		route.setId(new AgencyAndId(DEFAULT_AGENCY_ID, routeId));
		route.setShortName(routeName);
		route.setAgency(agency);
		route.setType(mode);
		queue.routes.add(route);
		
		List<ProtoRoute> protoRoutes = new ArrayList<ProtoRoute>();
		for(ExtendedFeature exft : group){
			// figure out spacing and speed for mode
			Double speed = config.getSpeed(exft);
	
			ProtoRoute protoroute = stopGenerator.makeProtoRoute(exft, speed);
			protoRoutes.add( protoroute );
		}

		Map<ProtoRouteStop, Stop> prsStops = new HashMap<ProtoRouteStop, Stop>();
		for(ProtoRoute protoroute : protoRoutes ){
			for (ProtoRouteStop prs : protoroute.ret) {
				// generate stops
				Stop stop = prs.stop;
				
				if (!queue.stops.contains(stop))
				    queue.stops.add(stop);
	
				prsStops.put(prs, stop);
			}
		}
		
		if( !config.isExact() ){
			makeFrequencyTrip(exemplar, protoRoutes, route, prsStops, false, config.usePeriods());
			if (config.isBidirectional()) {
				makeFrequencyTrip(exemplar, protoRoutes, route, prsStops, true, config.usePeriods());
			}
		} else {
			makeTimetableTrips(exemplar, protoRoutes, route, prsStops, false, config.usePeriods());
			if (config.isBidirectional()) {
				makeTimetableTrips(exemplar, protoRoutes, route, prsStops, true, config.usePeriods());
			}
		}

	}

	private void makeTimetableTrips(ExtendedFeature exft, List<ProtoRoute> protoRoutes, Route route,
			Map<ProtoRouteStop, Stop> prsStops, boolean reverse, boolean usePeriods) throws FeatureDoesntDefineTimeWindowException {
		// for each window
		for (ServiceWindow window : config.getServiceWindows()) {
			Double headway;
			try{
				headway = getHeadway(exft, window.propName, usePeriods);
			} catch (FeatureDoesntDefineTimeWindowException ex){
				System.out.println( "route id:"+route.getId().getId()+" has no value for time window "+window.propName );
				if( config.tolerant() ){
					continue;
				} else {
					throw ex;
				}
			}
			if(headway==null){
				continue;
			}
			
			// generate a series of trips
			for(int t=window.startSecs(); t<window.endSecs(); t+=headway){
				Trip trip = makeNewTrip(route, reverse);
				queue.trips.add(trip);
				
				int segStart = t;
				int firstStopTimeSeq=0;
				List<StopTime> stopTimes = new ArrayList<StopTime>();
				
				for(int i=0; i<protoRoutes.size(); i++){
					int index=i;
					if(reverse)
						index = protoRoutes.size()-1-i;
					
					ProtoRoute protoRoute = protoRoutes.get(index);
					
					List<StopTime> segStopTimes = createStopTimes(protoRoute.ret, prsStops, reverse, protoRoute.speed, trip, segStart, firstStopTimeSeq, protoRoute.length);
					stopTimes.addAll(segStopTimes);
					segStart += protoRoute.getDuration();
					firstStopTimeSeq += segStopTimes.size();
				}

				
				queue.stoptimes.addAll(stopTimes);
			}
		}
		
	}

	private void makeFrequencyTrip(ExtendedFeature exft, List<ProtoRoute> protoRoutes, Route route,
			Map<ProtoRouteStop, Stop> prsStops, boolean reverse, boolean usePeriods) throws FeatureDoesntDefineTimeWindowException {
		// generate a trip
		Trip trip = makeNewTrip(route, reverse);
		queue.trips.add(trip);

		// generate a frequency
		for (ServiceWindow window : config.getServiceWindows()) {
			Double headway;
			try{
				headway = getHeadway(exft, window.propName, usePeriods);
			} catch (FeatureDoesntDefineTimeWindowException ex){
				System.out.println( "feature for route id:"+route.getId().getId()+" does not define time window '"+window.propName+"'" );
				if( config.tolerant() ){
					continue;
				} else {
					throw ex;
				}
			}
			
			if (headway == null)
			    continue; // no service in this time window
			    
			headway /= config.waitFactor();
			
			Frequency freq = makeFreq(headway, window.startSecs(), window.endSecs(), trip);
			queue.frequencies.add(freq);
		}

		int segStart = 0;
		int firstStopTimeSeq=0;
		List<StopTime> newStopTimes = new ArrayList<StopTime>();
		for( ProtoRoute protoRoute : protoRoutes ){
			List<StopTime> segStopTimes = createStopTimes(protoRoute.ret, prsStops, reverse, protoRoute.speed, trip, segStart, firstStopTimeSeq, protoRoute.length);
			newStopTimes.addAll(segStopTimes);
			segStart += protoRoute.getDuration();
			firstStopTimeSeq += segStopTimes.size();
		}
				
		queue.stoptimes.addAll(newStopTimes);
		
	}

	private Trip makeNewTrip(Route route, boolean reverse) {
		Trip trip = new Trip();
		trip.setRoute(route);
		trip.setId(new AgencyAndId(DEFAULT_AGENCY_ID, String.valueOf(queue.trips.size())));
		trip.setServiceId(new AgencyAndId(DEFAULT_AGENCY_ID, DEFAULT_CAL_ID));
		if(reverse){
			trip.setDirectionId("1");
		} else {
			trip.setDirectionId("0");
		}
		return trip;
	}

	private static List<StopTime> createStopTimes(List<ProtoRouteStop> prss, Map<ProtoRouteStop, Stop> prsStops,
			boolean reverse, double speed, Trip trip, int tripStart, int firstStopTimeSequence, double segLen) {
		List<StopTime> newStopTimes = new ArrayList<StopTime>();
		for (int i = 0; i < prss.size(); i++) {

			int ix = i;
			if (reverse) {
				ix = prss.size() - 1 - i;
			}
			ProtoRouteStop prs = prss.get(ix);
			Stop stop = prsStops.get(prs);

			// generate stoptime
			StopTime stoptime = new StopTime();
			stoptime.setStop(stop);
			stoptime.setTrip(trip);
			stoptime.setStopSequence(i+firstStopTimeSequence);
			
			double dist;
			if(reverse){
				dist = segLen-prs.dist;
			} else {
				dist = prs.dist;
			}
			int time = (int) (dist / speed) + tripStart;
			
			stoptime.setArrivalTime(time);
			stoptime.setDepartureTime(time);
			
			newStopTimes.add(stoptime);
			
		}
		return newStopTimes;
	}

	private static Frequency makeFreq(double headway, int beginSecs, int endSecs, Trip trip) {
		Frequency freq;

		freq = new Frequency();
		freq.setStartTime(beginSecs);
		freq.setEndTime(endSecs);

		freq.setHeadwaySecs((int) (headway));

		freq.setTrip(trip);

		return freq;
	}

	private Double getHeadway(ExtendedFeature exft, String propName, boolean usePeriods) throws FeatureDoesntDefineTimeWindowException {
		double headway;
		String freqStr = exft.getProperty(propName);
		Double freqDbl;
		
		if (freqStr == null || freqStr.equals("None")) {
			freqDbl = config.getDefaultServiceLevel();			
			System.err.println("warning: using default frequency for feature " + exft.toString());
		} else {
			freqDbl = Double.parseDouble(freqStr);
		}
		
		if (freqDbl == 0.0 || freqDbl == null) {
			throw new FeatureDoesntDefineTimeWindowException(propName);
		}
		
		if (usePeriods) {
			headway = freqDbl * 60; // minutes to seconds
		} else {
			headway = 3600 / freqDbl;
		}
		return headway;
	}

	static List<Feature> getFeatures(String shp_filename) throws MalformedURLException, IOException {
		// construct shapefile factory
		File file = new File(shp_filename);
		Map<String, URL> map = new HashMap<String, URL>();
		map.put("url", file.toURI().toURL());
		DataStore dataStore = DataStoreFinder.getDataStore(map);

		// get shapefile as generic 'feature source'
		String typeName = dataStore.getTypeNames()[0];
		SimpleFeatureSource featureSource = dataStore.getFeatureSource(dataStore.getTypeNames()[0]);

		SimpleFeatureType schema = featureSource.getSchema();

		CoordinateReferenceSystem shpCRS = schema.getCoordinateReferenceSystem();
		
		SimpleFeatureCollection collection = featureSource.getFeatures();
		SimpleFeatureIterator iterator = collection.features();
		
		List<Feature> ret = new ArrayList<Feature>(collection.size());
		
		if (shpCRS != null && !shpCRS.equals(DefaultGeographicCRS.WGS84)) {
			try {
			MathTransform transform = CRS.findMathTransform(shpCRS, DefaultGeographicCRS.WGS84, true);

			while (iterator.hasNext()) {
				SimpleFeature next = iterator.next();
				
				Geometry geom = (Geometry) next.getDefaultGeometry();
				next.setDefaultGeometry(JTS.transform(geom, transform));
				
				ret.add(next);
			}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		else {
			while (iterator.hasNext()) {
				ret.add(iterator.next());
			}
		}	
			
		return ret;
	}

}
