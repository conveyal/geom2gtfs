package com.conveyal.geom2gtfs;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opengis.feature.GeometryAttribute;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;

public class PicketStopGenerator implements StopGenerator {
	
	private static final boolean FAIL_ON_MULTILINESTRING = true;

	private JSONObject data;

	public PicketStopGenerator(JSONObject data) {
		this.data = data;
	}

	private ProtoRoute makeProtoRouteStopsFromLinestring(LineString geom, double spacing) {
		ProtoRoute ret = new ProtoRoute();

		Coordinate[] coords = geom.getCoordinates();
		double overshot = 0;
		double segStartDist = 0;

		double totalLen = 0;
		for (int i = 0; i < coords.length - 1; i++) {
			Coordinate p1 = coords[i];
			Coordinate p2 = coords[i + 1];

			double segCurs = overshot;
			double segLen = GeoMath.greatCircle(p1, p2);
			totalLen += segLen;

			while (segCurs < segLen) {
				double index = segCurs / segLen;
				Coordinate interp = GeoMath.interpolate(p1, p2, index);

				ProtoRouteStop prs = new ProtoRouteStop(interp, segStartDist + segCurs);
				ret.add(prs);

				segCurs += spacing;
			}

			overshot = segCurs - segLen;

			segStartDist += segLen;
		}

		// add one final stop, at the very end
		ProtoRouteStop prs = new ProtoRouteStop(coords[coords.length - 1], totalLen);
		ret.add(prs);

		ret.length = totalLen;

		return ret;
	}

	public ProtoRoute makeProtoRoute(ExtendedFeature exft, Double speed) throws Exception {
		GeometryAttribute geomAttr = exft.feat.getDefaultGeometryProperty();
		MultiLineString geom = (MultiLineString) geomAttr.getValue();

		if (FAIL_ON_MULTILINESTRING && geom.getNumGeometries() > 1) {
			throw new Exception("Features may only contain a single linestring.");
		}
		
		double spacing = Config.getSpacing(exft, this.data);

		LineString ls = (LineString) geom.getGeometryN(0);
		ProtoRoute ret = this.makeProtoRouteStopsFromLinestring(ls, spacing);
		ret.speed = speed;
		return ret;
	}

}
