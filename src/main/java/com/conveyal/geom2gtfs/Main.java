package com.conveyal.geom2gtfs;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import org.onebusaway.gtfs.model.ServiceCalendar;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.serialization.GtfsWriter;
import org.opengis.feature.Feature;
import org.opengis.feature.GeometryAttribute;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;

public class Main {

	private static final String DEFAULT_AGENCY_ID = "0";
	private static final String DEFAULT_CAL_ID = "0";
	static Config config;

	static GtfsQueue queue = null;

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.out.println("usage: cmd shapefile_fn config_fn output_fn");
			return;
		}

		String fn = args[0];
		String config_fn = args[1];
		String output_fn = args[2];

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

		FeatureSource<?, ?> lines = getFeatureSource(fn);

		FeatureCollection<?, ?> featCol = lines.getFeatures();
		FeatureIterator<?> features = featCol.features();
		
		List<ExtendedFeature> extFeatures = joinFeatures( features, csvJoin );
		
		extFeatures = filterFeatures( extFeatures );
		
		Map<String, List<ExtendedFeature>> featureGroups = groupFeatures( extFeatures );
		
		for( List<ExtendedFeature> group : featureGroups.values() ){
			
			ExtendedFeature exemplar = group.get(0);

			System.out.println("generating elements for \"" + exemplar.getProperty(config.getRouteNamePropName())
					+ "\"");

			featToGtfs(group, agency);
		}

		System.out.println( "writing to "+output_fn );
		GtfsWriter gtfsWriter = new GtfsWriter();
		gtfsWriter.setOutputLocation(new File(output_fn));
		queue.dumpToWriter(gtfsWriter);
		gtfsWriter.close();
		System.out.println( "done" );
	}

	private static Map<String, List<ExtendedFeature>> groupFeatures(List<ExtendedFeature> extFeatures) {
		Map<String, List<ExtendedFeature>> ret = new HashMap<String, List<ExtendedFeature>>();
		
		// gather by route id
		for( ExtendedFeature exft : extFeatures ){
			String id = exft.getProperty( config.getRouteIdPropName() );
			
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

	private static List<ExtendedFeature> filterFeatures(List<ExtendedFeature> extFeatures) {
		List<ExtendedFeature> ret = new ArrayList<ExtendedFeature>();
		
		for( ExtendedFeature exft : extFeatures ){
			if (config.passesFilter(exft)) {
				ret.add(exft);
			}
		}
		
		return ret;
	}

	private static List<ExtendedFeature> joinFeatures(FeatureIterator<?> features, CsvJoinTable csvJoin) {
		List<ExtendedFeature> ret = new ArrayList<ExtendedFeature>();
		
		while (features.hasNext()) {
			Feature feat = (Feature) features.next();

			ExtendedFeature exft = new ExtendedFeature(feat, csvJoin);
			
			ret.add( exft );
		}
		
		return ret;
	}

	private static void featToGtfs(List<ExtendedFeature> group, Agency agency) throws Exception {
		
		ExtendedFeature exemplar = group.get(0);

		// get route type
		Integer mode = config.getMode(exemplar);

		// generate route
		String routeId = exemplar.getProperty(config.getRouteIdPropName());
		String routeName = exemplar.getProperty(config.getRouteNamePropName());
		Route route = new Route();
		route.setId(new AgencyAndId(DEFAULT_AGENCY_ID, routeId));
		route.setShortName(routeName);
		route.setAgency(agency);
		route.setType(mode);
		queue.routes.add(route);
		
		List<ProtoRoute> protoRoutes = new ArrayList<ProtoRoute>();
		StopGenerator stops = config.getStopGenerator();
		for(ExtendedFeature exft : group){
			// figure out spacing and speed for mode
			Double speed = config.getSpeed(exft);
	
			ProtoRoute protoroute = stops.makeProtoRoute(exft, speed, routeId);
			protoRoutes.add( protoroute );
		}

		Map<ProtoRouteStop, Stop> prsStops = new HashMap<ProtoRouteStop, Stop>();
		for(ProtoRoute protoroute : protoRoutes ){
			for (ProtoRouteStop prs : protoroute.ret) {
				// generate stops
				Stop stop = new Stop();
				stop.setLat(prs.coord.y);
				stop.setLon(prs.coord.x);
				stop.setId(new AgencyAndId(DEFAULT_AGENCY_ID, String.valueOf(queue.stops.size())));
				stop.setName(String.valueOf(queue.stops.size()));
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

	private static void makeTimetableTrips(ExtendedFeature exft, List<ProtoRoute> protoRoutes, Route route,
			Map<ProtoRouteStop, Stop> prsStops, boolean reverse, boolean usePeriods) {
		// for each window
		for (ServiceWindow window : config.getServiceWindows()) {
			Double headway = getHeadway(exft, window.propName, usePeriods);
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
					
					List<StopTime> segStopTimes = createStopTimes(protoRoute.ret, prsStops, reverse, protoRoute.speed, trip, segStart, firstStopTimeSeq);
					stopTimes.addAll(segStopTimes);
					segStart += protoRoute.getDuration();
					firstStopTimeSeq += segStopTimes.size();
				}

				
				queue.stoptimes.addAll(stopTimes);
			}
		}
		
	}

	private static void makeFrequencyTrip(ExtendedFeature exft, List<ProtoRoute> protoRoutes, Route route,
			Map<ProtoRouteStop, Stop> prsStops, boolean reverse, boolean usePeriods) {
		// generate a trip
		Trip trip = makeNewTrip(route, reverse);
		queue.trips.add(trip);

		// generate a frequency
		for (ServiceWindow window : config.getServiceWindows()) {
			Double headway = getHeadway(exft, window.propName, usePeriods);
			if(headway==null){
				continue;
			}
			
			headway /= config.waitFactor();
			
			Frequency freq = makeFreq(headway, window.startSecs(), window.endSecs(), trip);
			queue.frequencies.add(freq);
		}

		int segStart = 0;
		int firstStopTimeSeq=0;
		List<StopTime> newStopTimes = new ArrayList<StopTime>();
		for( ProtoRoute protoRoute : protoRoutes ){
			List<StopTime> segStopTimes = createStopTimes(protoRoute.ret, prsStops, reverse, protoRoute.speed, trip, segStart, firstStopTimeSeq);
			newStopTimes.addAll(segStopTimes);
			segStart += protoRoute.getDuration();
			firstStopTimeSeq += segStopTimes.size();
		}
				
		queue.stoptimes.addAll(newStopTimes);
		
	}

	private static Trip makeNewTrip(Route route, boolean reverse) {
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
			boolean reverse, double speed, Trip trip, int tripStart, int firstStopTimeSequence) {
		List<StopTime> newStopTimes = new ArrayList<StopTime>();
		double firstStopDist = 0;
		for (int i = 0; i < prss.size(); i++) {

			int ix = i;
			if (reverse) {
				ix = prss.size() - 1 - i;
			}
			ProtoRouteStop prs = prss.get(ix);
			if (i == 0) {
				firstStopDist = prs.dist;
			}
			Stop stop = prsStops.get(prs);

			// generate stoptime
			StopTime stoptime = new StopTime();
			stoptime.setStop(stop);
			stoptime.setTrip(trip);
			stoptime.setStopSequence(i+firstStopTimeSequence);
			double dist = Math.abs(prs.dist - firstStopDist);
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

	private static Double getHeadway(ExtendedFeature exft, String propName, boolean usePeriods) {
		double headway;
		String freqStr = exft.getProperty(propName);
		if (freqStr == null || freqStr.equals("None")) {
			return null;
		}
		Double freqDbl = Double.parseDouble(freqStr);
		if (freqDbl == 0.0) {
			return null;
		}
		if (usePeriods) {
			headway = Double.parseDouble(freqStr) * 60; // minutes to seconds
		} else {
			double perHour = Double.parseDouble(freqStr); // arrivals per hour
			headway = 3600 / perHour;
		}
		return headway;
	}

	private static FeatureSource<?, ?> getFeatureSource(String shp_filename) throws MalformedURLException, IOException {
		// construct shapefile factory
		File file = new File(shp_filename);
		Map<String, URL> map = new HashMap<String, URL>();
		map.put("url", file.toURI().toURL());
		DataStore dataStore = DataStoreFinder.getDataStore(map);

		// get shapefile as generic 'feature source'
		String typeName = dataStore.getTypeNames()[0];
		FeatureSource<?, ?> source = dataStore.getFeatureSource(typeName);
		return source;
	}

}