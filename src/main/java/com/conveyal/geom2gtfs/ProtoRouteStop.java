package com.conveyal.geom2gtfs;

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Stop;

import com.vividsolutions.jts.geom.Coordinate;

import java.util.concurrent.atomic.AtomicInteger;

public class ProtoRouteStop {
        // TODO: this is kind of hacky, we should really be using a separate ID space for each feed.
        private static AtomicInteger createdStopId = new AtomicInteger();
    
        public ProtoRouteStop (Coordinate coord, double dist) {
            this.coord = coord;
            this.dist = dist;
            this.stop = new Stop();
            stop.setLat(coord.y);
            stop.setLon(coord.x);
            int stopId = createdStopId.incrementAndGet();
            stop.setName("Stop " + stopId);
            stop.setId(new AgencyAndId(Main.DEFAULT_AGENCY_ID, "created_stop_" + stopId));
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
