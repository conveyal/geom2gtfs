package com.conveyal.geom2gtfs;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.onebusaway.csv_entities.EntityHandler;
import org.onebusaway.csv_entities.exceptions.CsvEntityIOException;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.serialization.GtfsReader;
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
import com.vividsolutions.jts.linearref.LinearLocation;
import com.vividsolutions.jts.linearref.LocationIndexedLine;

/**
 * A stop generator that creates stops at roughly the requested spacing
 * (within the requested tolerance), and shares stops between generated routes.
 *   
 * @author mattwigway
 */
public class ClusterStopGenerator implements StopGenerator {
    // this is final because the subclass stoploader accesses it.
    public final SpatialIndex stopIndex;
    public SpatialIndex wayIndex;
    
    private JSONObject data;
    
    private static GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(PrecisionModel.FIXED), 4326);
    /**
     * How far to each side of the route we look for candidate stops, in meters.
     */
    public double threshold;
    
    /**
     * Should we create stops if we can't find anything to snap to?
     */
    public boolean createUnmatchedStops;
    
    public ClusterStopGenerator(JSONObject data) {
        this.data = data;
        
        stopIndex = new Quadtree();
        wayIndex = new Quadtree();
        threshold = data.has("threshold") ? data.getDouble("threshold") : 100D;
        createUnmatchedStops = data.has("create_stops") ? data.getBoolean("create_stops") : true;
        
        // Load OSM files
        if (data.has("osmfiles")) {
         /**   JSONArray files = data.getJSONArray("osmfiles");
            for (int i = 0; i < files.length(); i++) {
                String fn = files.getString(i);
                System.err.println("Processing OSM file " + fn);
                
                OSM osm = OSM.fromPBF(fn);
                osm.findIntersections();
                
                for (Way way : osm.ways.values()) {
                    // todo: pedestrian = no
                    if (way.hasTag("highway") && way.getTag("highway") != "motorway"
                            && way.nodes.length >= 2) {
                        
                        // create wayinfo                        
                        WayInfo wi = new WayInfo();
                        wi.coords = new Coordinate[way.nodes.length];
                        wi.intersections = new boolean[way.nodes.length];
                        
                        for (int coordIdx = 0; coordIdx < way.nodes.length; coordIdx++) {
                            Node node = osm.nodes.get(way.nodes[coordIdx]);
                            wi.coords[coordIdx] = new Coordinate(node.lon, node.lat);
                            wi.intersections[coordIdx] = osm.intersections.contains(way.nodes[coordIdx]);
                        }
                        
                        LineString ls = geometryFactory.createLineString(wi.coords);                                                
                        wayIndex.insert(ls.getEnvelopeInternal(), wi);
                    }
                }
            }**/
        }
        
        // Load GTFS files        
        if (data.has("gtfsfiles")) {
        
            JSONArray files = data.getJSONArray("gtfsfiles");
            for (int fileIdx = 0; fileIdx < files.length(); fileIdx++) {
                String fileName = files.getString(fileIdx);
                System.err.println("Processing GTFS file " + fileName);
                
                try {
                    GtfsReader reader = new GtfsReader();
                    reader.setInputLocation(new File(fileName));
                    reader.addEntityHandler(new StopLoader(fileIdx));
                    reader.run();
                } catch (CsvEntityIOException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof AllStopsLoadedException) {
                        // this is expected; all the stops have been loaded.
                        System.err.println("Loaded " + ((AllStopsLoadedException) cause).count + " stops" );
                    } else {
                        // rethrow
                        throw e;
                    }
                } catch (IOException e) {
                    System.err.println("Unable to load GTFS file " + fileName);
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
        
        out.length = metersAlongLine[metersAlongLine.length - 1];
        
        double spacing = Config.getSpacing(exft, this.data);
        
        // find stops near each "ideal" location on the line
        for (double offset = 0; offset < metersAlongLine[metersAlongLine.length - 1]; offset += spacing) {
            // find the point for which we want to find a protoroutestop
            int right = 1;
            while (metersAlongLine[right] < offset && right < metersAlongLine.length)
                right++;
            
            double length = (metersAlongLine[right] - metersAlongLine[right - 1]);
            
            Coordinate ideal;
            if (length > 0.00000000001) {
                double frac = (offset - metersAlongLine[right - 1]) / length;
                ideal = GeoMath.interpolate(coords[right - 1], coords[right], frac);
            }
            else {
                // We have landed with our ideal stop exactly on top of two duplicated coordinates.
                // While this seems improbable, it can happen if the coordinates at the start are duplicated
                ideal = coords[right];
            }
            
            ProtoRouteStop prs = getProtoRouteStopForCoord(ideal);
            
            if (prs != null) {
                // don't add the same stop twice in a row.
                if (!out.ret.isEmpty() && prs.stop.equals(out.ret.get(out.ret.size() - 1).stop))
                    continue;
                
                // set the distance appropriately
                // away from this one, not from some ideal location.
                // Note that we do not set the distance to this stop, but rather the distance to the ideal location
                // of this stop. this avoids issues with loop routes, where the same stop might have multiple distances
                // distance just needs to be monotonically increasing, no need to be super-accurate in a ratio sense.
                prs.dist = offset;
                out.add(prs);
            }
        }
        
        return out;
    }

    /**
     * Find the best stop near the given coordinate.
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
        

        // Look for nearby ways
        
        
        @SuppressWarnings("unchecked")
        List<WayInfo> ways = wayIndex.query(env);

        Coordinate bestPoint = null;
        
        if (!ways.isEmpty()) {
            // OK, snap to nearest way
            // note that the spatial index only contains walkable ways

            Coordinate point;
            int left, right;
            LinearLocation loc;
            double bestDistance = Double.MAX_VALUE;
            double dist, leftDist, rightDist;
            
            for (WayInfo wayInfo : ways) {
                LocationIndexedLine way =
                        new LocationIndexedLine(geometryFactory.createLineString(wayInfo.coords));
                loc = way.project(ideal);
                point = way.extractPoint(loc);
                dist = GeoMath.greatCircle(point, ideal);

                if (dist < bestDistance && dist <= threshold) {
                    bestDistance = dist;
                    bestPoint = point;
                    
                    // we don't blithely add one to the right segment index, because this isn't
                    // actually a segment index; if the point is past the end of the line the
                    // segment index is the index of the last coordinate
                    // AFAIK segment index cannot be negative.
                    left = right = loc.getSegmentIndex();
                    
                    // but check and give a useful error message in case my assumption is incorrect.
                    if (left < 0) {
                        throw new RuntimeException("Got negative segment index.");
                    }
                    
                    // find the next and previous intersections, if they exist
                    while (!wayInfo.intersections[left] && left > 0) left--;
                    while (!wayInfo.intersections[right] && right < wayInfo.coords.length - 1) right++;
                    
                    leftDist = GeoMath.greatCircle(wayInfo.coords[left], ideal);
                    rightDist = GeoMath.greatCircle(wayInfo.coords[right], ideal);
                    
                    if ((left == right || leftDist <= rightDist) && leftDist <= threshold) {
                        // we don't reset bestDistance but instead leave it as the distance to the
                        // nearest point on the way. So we're saying "snap to an intersection, if possible,
                        // on the closest way"
                        bestPoint = wayInfo.coords[left];
                    }
                    
                    else if (rightDist <= leftDist && rightDist <= threshold) {
                        bestPoint = wayInfo.coords[right];
                    }
                    
                }
            }
        }
        
        // if we didn't find anything to snap to, deal with it
        if (bestPoint == null) {
            if (createUnmatchedStops) {
                bestPoint = ideal;
            }
            else {
                System.err.println("Could not find stop location near " + ideal.y + ", " + ideal.x);
                return null;
            }
        }
        
        ProtoRouteStop prs = new ProtoRouteStop(bestPoint, 0);
        
        // add the newly-created stop to the index
        stopIndex.insert(new Envelope(prs.coord), prs.stop);
        
        return prs;
    }
    
    /**
     * Holds just enough information about a way to be able to snap to its nodes.
     * @author mattwigway
     *
     */
    private static class WayInfo {
        public Coordinate[] coords;
        
        /** intersections[i] is true if node i is an intersection, false otherwise */
        public boolean[] intersections;
    }
    
    /**
     * Load just the stops from a GTFS feed.
     * 
     * Once all stops have been loaded, raises an AllStopsLoadedException to prevent loading the
     * rest of the feed.
     */
    private class StopLoader implements EntityHandler {
        private int gtfsFile;
        private int count;
        
        /**
         * Construct a new StopLoader
         * @param gtfsFile Stop IDs will be prefixed with this to avoid namespace collisions.
         */
        public StopLoader (int gtfsFile) {
            count = 0;
            this.gtfsFile = gtfsFile;
        }
        
        @Override
        public void handleEntity(Object o) {
            if (o instanceof Stop) {
                Stop stop = (Stop) o;
                
                // don't snap to stations
                if (stop.getLocationType() == 1)
                    return;
                
                count++;
                
                // referential integrity
                stop.setParentStation(null);
                
                Coordinate coord = new Coordinate(stop.getLon(), stop.getLat());
                
                AgencyAndId existingId = stop.getId();
                AgencyAndId newId =
                        new AgencyAndId(Main.DEFAULT_AGENCY_ID, 
                                "" + gtfsFile + "_" + existingId.getId()
                                );

                stop.setId(newId);
                
                stopIndex.insert(new Envelope(coord), stop);
            }
            else if (count > 0) {
                // OBA processes the files sequentially, so if we have seen a stop in the past
                // but this is not a stop, we are done

                throw new AllStopsLoadedException(count);
            }
        }
    }
    
    /**
     * Indicates that all the stops have been loaded.
     * 
     * This is unchecked because we can't throw an exception inside HandleEntity.
     */
    private static class AllStopsLoadedException extends RuntimeException {
        public int count;
        
        public AllStopsLoadedException (int count) {
            this.count = count;
        }
    }
}
