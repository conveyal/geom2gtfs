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
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.GeodeticCalculator;
import org.onebusaway.gtfs.model.Stop;
import org.opengis.feature.Feature;
import org.opengis.feature.GeometryAttribute;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

public class Main {

	private static final double STOP_SPACING = 400; //meters

	public static void main(String[] args) throws MalformedURLException, IOException, TransformException {
		if(args.length < 1){
    		System.out.println( "usage: cmd shapefile_fn" );
    		return;
		}
		
		String fn = args[0];
				
		FeatureSource<?, ?> lines = getFeatureSource(fn);
		
        FeatureCollection<?, ?> featCol = lines.getFeatures();
        FeatureIterator<?> features = featCol.features();
        
        CoordinateReferenceSystem crs = lines.getSchema().getCoordinateReferenceSystem();
        
        List<ProtoRouteStop> allStops = new ArrayList<ProtoRouteStop>();
        while( features.hasNext() ){
             Feature feat = (Feature) features.next();
             
             System.out.println( "generating stops for \""+feat.getProperty("ROUTE_NAME").getValue() + "\"" );
             
             GeometryAttribute geomAttr = feat.getDefaultGeometryProperty();
             MultiLineString geom = (MultiLineString) geomAttr.getValue();
             System.out.println( geomLen( geom, crs ) );
             
             List<ProtoRouteStop> stops = makeStops( geom, crs, STOP_SPACING, feat.getProperty("ROUTE_ID").getValue().toString() );
             allStops.addAll( stops );
             
             System.out.println( "" );

        }
        
        PrintWriter writer = new PrintWriter("out.csv", "UTF-8");
		writer.println("lon,lat,stop_id");
		for( ProtoRouteStop prs : allStops ){
			writer.println( prs.coord.x+","+prs.coord.y+","+prs.stopId );
		}
		writer.close();
		
	}
	
	private static List<ProtoRouteStop> makeStops(MultiLineString geom, CoordinateReferenceSystem crs, double spacing, String stopId) throws TransformException {
		List<ProtoRouteStop> ret = new ArrayList<ProtoRouteStop>();
		
		LengthIndexedLine chopper = new LengthIndexedLine( geom );
		
		double len = geomLen( geom, crs );
		double nSpans = Math.ceil(len/spacing);
		double spanLength = len/nSpans;
		
		double startIndex = chopper.getStartIndex();
		double endIndex = chopper.getEndIndex();
		
		double indexSpanLength = (endIndex-startIndex)/nSpans;
		System.out.println( "indexSpanLength: "+indexSpanLength );
		
		for(int i=0; i<nSpans+1; i++){
			Coordinate coord = chopper.extractPoint( i*indexSpanLength );
			
			ProtoRouteStop prs = new ProtoRouteStop();
			prs.coord = coord;
			prs.dist = spanLength * i;
			prs.stopId = stopId;
			ret.add( prs );
		}
		
		return ret;
	}

	private static double geomLen(MultiLineString geom, CoordinateReferenceSystem crs) throws TransformException {
		GeodeticCalculator gc = new GeodeticCalculator(crs);
		
		double len = 0;
		Coordinate[] coords = geom.getCoordinates();
		for(int i=0; i<coords.length-1; i++){
			Coordinate p1 = coords[0];
			Coordinate p2 = coords[1];
			
			gc.setStartingPosition( JTS.toDirectPosition(p1, crs) );
			gc.setDestinationPosition( JTS.toDirectPosition(p2, crs) );
			
			len += gc.getOrthodromicDistance();
		}
		
		return len;
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
	
}