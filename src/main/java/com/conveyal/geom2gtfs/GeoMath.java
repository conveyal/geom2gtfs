package com.conveyal.geom2gtfs;

import com.vividsolutions.jts.geom.Coordinate;

public class GeoMath {
	static double greatCircle(double x1deg, double y1deg, double x2deg, double y2deg){
        double x1 = Math.toRadians(x1deg);
        double y1 = Math.toRadians(y1deg);
        double x2 = Math.toRadians(x2deg);
        double y2 = Math.toRadians(y2deg);
		
		// haversine formula
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
	
	static Coordinate interpolate(Coordinate p1, Coordinate p2,
			double index) {
		
		double x = (p2.x-p1.x)*index + p1.x;
		double y = (p2.y-p1.y)*index + p1.y;
		
		return new Coordinate(x,y);
	}
	
	/**
	 * The maximum number of degrees that is needed to represent the given threshold (in meters) at the given latitude.
	 */
	static double upperBoundDegreesForThreshold(double latitude, double threshold) {
	    // get the number of meters in a degree of longitude at this latitude
	    double metersPerDegree = Math.PI * Math.cos(Math.toRadians(latitude)) * 6371000 / 180;
	    return threshold / metersPerDegree;
	}
}
