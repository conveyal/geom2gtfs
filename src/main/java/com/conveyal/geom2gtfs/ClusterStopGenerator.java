package com.conveyal.geom2gtfs;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.onebusaway.gtfs.model.Stop;
import org.opengis.feature.GeometryAttribute;
import org.opentripplanner.osm.Node;
import org.opentripplanner.osm.OSM;
import org.opentripplanner.osm.Way;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.index.SpatialIndex;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.linearref.LinearLocation;
import com.vividsolutions.jts.linearref.LocationIndexedLine;

/**
 * A stop generator that creates stops at roughly the requested spacing
 * (within the requested tolerance), and shares stops between generated routes.
 *   
 * @author mattwigway
 */
public class ClusterStopGenerator implements StopGenerator {
    public SpatialIndex stopIndex;
    
    public SpatialIndex wayIndex;
    
    private static GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(PrecisionModel.FIXED), 4326);
    /**
     * How far to each side of the route we look for candidate stops, in meters.
     */
    private double threshold;
    
    public ClusterStopGenerator(JSONObject data) {
        stopIndex = new Quadtree();
        wayIndex = new Quadtree();
        threshold = data.getDouble("threshold");
        
        // Load OSM file
        if (data.has("osmfiles")) {
            JSONArray files = data.getJSONArray("osmfiles");
            for (int i = 0; i < files.length(); i++) {
                String fn = files.getString(i);
                System.err.println("Processing OSM file " + fn);
                
                OSM osm = OSM.fromPBF(fn);
                
                for (Way way : osm.ways.values()) {
                    // todo: pedestrian = no
                    if (way.hasTag("highway") && way.getTag("highway") != "motorway"
                            && way.nodes.length >= 2) {
                        // create a linestring
                        Coordinate[] coords = new Coordinate[way.nodes.length];
                        
                        for (int coordIdx = 0; coordIdx < way.nodes.length; coordIdx++) {
                            Node node = osm.nodes.get(way.nodes[coordIdx]);
                            coords[coordIdx] = new Coordinate(node.lon, node.lat);
                        }
                        
                        LineString ls = geometryFactory.createLineString(coords);
                        LocationIndexedLine ils = new LocationIndexedLine(ls);
                        wayIndex.insert(ls.getEnvelopeInternal(), ils);
                    }
                }
            }
        }
    }
    
    @Override
    public ProtoRoute makeProtoRoute(ExtendedFeature exft, Double speed) throws Exception {
        ProtoRoute out = new ProtoRoute();
        out.speed = speed;
        
        // find candidate stops
        GeometryAttribute geomAttr = exft.feat.getDefaultGeometryProperty();
        MultiLineString geom = (MultiLineString) geomAttr.getValue();
        
        LineString ls = (LineString) geom.getGeometryN(0);
        LocationIndexedLine ils = new LocationIndexedLine(ls);
                
        // figure out the offsets to each coordinate in the line
        Coordinate[] coords = ls.getCoordinates();
        double[] metersAlongLine = new double[coords.length];
        
        metersAlongLine[0] = 0; // by construction, the first coordinate is 0 meters along the line
        
        for (int i = 1; i < coords.length; i++) {
            metersAlongLine[i] = metersAlongLine[i - 1] + GeoMath.greatCircle(coords[i - 1], coords[i]);
        }
        
        double spacing = 400;//this.getSpacing();
        
        // find stops near each "ideal" location on the line
        for (double offset = 0; offset < metersAlongLine[metersAlongLine.length - 1]; offset += spacing) {
            // find the point for which we want to find a protoroutestop
            int right = 1;
            while (metersAlongLine[right] < offset && right < metersAlongLine.length)
                right++;
            
            double frac = (offset - metersAlongLine[right - 1]) / (metersAlongLine[right] - metersAlongLine[right - 1]);
            
            // this is the ideal location of the stop, where it would be if the spacing was respected perfectly
            Coordinate ideal = GeoMath.interpolate(coords[right - 1], coords[right], frac);
            
            ProtoRouteStop prs = getProtoRouteStopForCoord(ideal);
            
            if (prs != null) {
                // don't add the same stop twice in a row.
                if (!out.ret.isEmpty() && prs.stop.equals(out.ret.get(out.ret.size() - 1).stop))
                    continue;
                
                // set the distance appropriately
                // away from this one, not from some ideal location.
                LinearLocation loc = ils.project(prs.coord);
                
                // the segment index is also the index of the previous coordinate
                // TODO: handle loop routes in snapping
                int left = loc.getSegmentIndex();
                
                // find the distance _along the segment_ to this point
                // since we snapped it, the distance along the segment is the same as the distance
                double metersAlongSegment = GeoMath.greatCircle(coords[left], prs.coord);
                prs.dist = metersAlongLine[left] + metersAlongSegment;
                out.add(prs);
            }
        }
        
        return out;
    }

    /**
     * Find the best stop in csls, or create a stop.
     * 
     * Note that the dist will not be set correctly.
     */
    private ProtoRouteStop getProtoRouteStopForCoord(Coordinate ideal) {
        // first look for existing nearby stops
        Envelope env = new Envelope(ideal);
        
        // get the upper bound
        double thresholdDegrees = GeoMath.upperBoundDegreesForThreshold(ideal.y, threshold);
        env.expandBy(thresholdDegrees);
        
        @SuppressWarnings("unchecked")
        List<Stop> stops = stopIndex.query(env);
        
        if (!stops.isEmpty()) {
            // hooray we found existing stops!
            double bestDistance = Double.MAX_VALUE;
            Stop best = null;
            
            for (Stop stop : stops) {
                double dist = GeoMath.greatCircle(stop.getLon(), stop.getLat(), ideal.x, ideal.y);
                
                if (dist < bestDistance && dist <= threshold) {
                    bestDistance = dist;
                    best = stop;
                }
            }
            
            if (best != null) {
                return new ProtoRouteStop(best, 0);
            }
        }
        
        // OK, so we didn't find a stop. Look for an OSM way.
        @SuppressWarnings("unchecked")
        List<LocationIndexedLine> ways = wayIndex.query(env);
        
        if (!ways.isEmpty()) {
            // OK, snap to nearest way
            // note that the spatial index only contains walkable ways
            Coordinate bestPoint = null;
            double bestDistance = Double.MAX_VALUE;
            
            for (LocationIndexedLine way : ways) {
                Coordinate point = way.extractPoint(way.project(ideal));
                double dist = GeoMath.greatCircle(point, ideal);
                
                if (dist < bestDistance && dist <= threshold) {
                    bestDistance = dist;
                    bestPoint = point;
                }
            }
            
            if (bestPoint != null) {
                ProtoRouteStop prs = new ProtoRouteStop(bestPoint, 0);
                // add it to the spatial index for future searches
                stopIndex.insert(new Envelope(prs.coord), prs.stop);
                
                return prs;
            }
        }
        
        // TODO: create stop from scratch if desired
        System.err.println("Could not find stop location near " + ideal.y + ", " + ideal.x);
        return null;
    }    
}
