package com.conveyal.geom2gtfs;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.geotools.data.FeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.referencing.GeodeticCalculator;
import org.geotools.referencing.datum.DefaultEllipsoid;
import org.json.JSONObject;
import org.opengis.feature.Feature;
import org.opengis.feature.GeometryAttribute;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.linearref.LinearLocation;
import com.vividsolutions.jts.linearref.LocationIndexedLine;

public class ShapefileStopGenerator implements StopGenerator {
	
	static boolean FAIL_ON_MULTILINESTRING = true;
	
	double threshold;
	List<Feature> stops;
	
	GeodeticCalculator gc = new GeodeticCalculator(DefaultEllipsoid.WGS84);

	public ShapefileStopGenerator(JSONObject data) throws MalformedURLException, IOException {
		String filename = data.getString("filename");
		threshold = data.getDouble("threshold");
		
		// collect all features from shapefile
		FeatureSource<?, ?> lines = Main.getFeatureSource(filename);
		FeatureCollection<?, ?> featCol = lines.getFeatures();
		FeatureIterator<?> features = featCol.features();
		stops = new ArrayList<Feature>();
		while (features.hasNext()) {
			Feature feat = (Feature) features.next();
			stops.add(feat);
		}
		
	}

	@Override
	public ProtoRoute makeProtoRoute(ExtendedFeature exft, Double speed) throws Exception {
		GeometryAttribute geomAttr = exft.feat.getDefaultGeometryProperty();
		MultiLineString geom = (MultiLineString) geomAttr.getValue();

		if (FAIL_ON_MULTILINESTRING && geom.getNumGeometries() > 1) {
			throw new Exception("Features may only contain a single linestring.");
		}
		
		LineString ls = (LineString) geom.getGeometryN(0);
		ProtoRoute ret = this.makeProtoRouteStopsFromLinestring(ls);
		ret.speed = speed;
				
		return ret;
	}

	private ProtoRoute makeProtoRouteStopsFromLinestring(LineString ls) {
		LocationIndexedLine ils = new LocationIndexedLine(ls);
		
		// create buffer of linestring
		Geometry buffer = ls.buffer( threshold ); // note distance is in same units as geoemtry
		
		// get all features in stop shapefile that fall within buffer
		List<Feature> nearbyStops = new ArrayList<Feature>();
		for(Feature stop : stops){
			Geometry stopGeom = (Geometry)stop.getDefaultGeometryProperty().getValue();
			if(stopGeom.within(buffer)){
				nearbyStops.add(stop);
			}
		}
		
		// for each feature, reference along the the linestring
		List<ProtoRouteStop> prss = new ArrayList<ProtoRouteStop>();
		for(Feature feature : nearbyStops ){
			Geometry geom = (Geometry) feature.getDefaultGeometryProperty().getValue();
			LinearLocation ix = ils.project(geom.getCoordinate());
			ProtoRouteStop prs = generateProtoRouteStop( ls, ix );
			prss.add(prs);
		}
		
		// sort linearly along linestring
		Collections.sort(prss, new Comparator<ProtoRouteStop>(){

			@Override
			public int compare(ProtoRouteStop o1, ProtoRouteStop o2) {
				if(o2.dist>o1.dist){
					return -1;
				} else if(o1.dist>o2.dist){
					return 1;
				} else {
					return 0;
				}
			}
			
		});
		
		// copy to return structure while removing duplicates
		ProtoRoute ret = new ProtoRoute();
		double lastDist=-1;
		for(ProtoRouteStop prs : prss){
			if(prs.dist != lastDist){
				ret.add(prs);
				lastDist = prs.dist;
			}
		}
		
		ret.length = distAlongLineString( ls, ils.getEndIndex() );
		
		return ret;
	}

	private ProtoRouteStop generateProtoRouteStop(LineString ls, LinearLocation ix) {
		double dist = distAlongLineString( ls, ix );
		
		ProtoRouteStop prs = new ProtoRouteStop(ix.getCoordinate(ls), dist);
		
		return prs;
	}

	private double distAlongLineString(LineString ls, LinearLocation ix) {
		double dist=0;
		
		int seg = ix.getSegmentIndex();
		
		double segDist=0;
		for(int i=0; i<seg; i++){
			Coordinate segStart = ls.getCoordinateN(i);
			Coordinate segEnd = ls.getCoordinateN(i+1);
			
			segDist = getSegLength( segStart, segEnd );
			
			if(i==seg-1){
				break; // we wanted the seg endpoints but not to add to the length total
			}
			
			dist += segDist;
		}
		
		double segFraction = ix.getSegmentFraction();
		dist += segFraction*segDist;
		
		return dist;
	}

	private double getSegLength(Coordinate segStart, Coordinate segEnd) {
		gc.setStartingGeographicPoint(segStart.x,segStart.y);
		gc.setDestinationGeographicPoint(segEnd.x,segEnd.y);
		return gc.getOrthodromicDistance();
	}

}
