package com.conveyal.geom2gtfs;

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Stop;

import com.vividsolutions.jts.geom.Coordinate;

public class ProtoRouteStop {
        private static int createdStopId = 0;
    
        public ProtoRouteStop (Coordinate coord, double dist) {
            this.coord = coord;
            this.dist = dist;
            this.stop = new Stop();
            stop.setLat(coord.y);
            stop.setLon(coord.x);
            stop.setName("Stop " + createdStopId++);
            stop.setId(new AgencyAndId(Main.DEFAULT_AGENCY_ID, "created_stop_" + createdStopId));
        }
        
        public ProtoRouteStop (Stop stop, double dist) {
            this.stop = stop;
            this.coord = new Coordinate(stop.getLon(), stop.getLat());
            this.dist = dist;
        }
    
	public Coordinate coord;
	public double dist;
	public Stop stop;
}
