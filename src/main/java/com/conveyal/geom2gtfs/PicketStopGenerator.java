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

	private ProtoRoute makeProtoRouteStopsFromLinestring(LineString geom, double spacing, String routeId) {
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

				ProtoRouteStop prs = new ProtoRouteStop();
				prs.coord = interp;
				prs.routeId = routeId;
				prs.dist = segStartDist + segCurs;
				ret.add(prs);

				segCurs += spacing;
			}

			overshot = segCurs - segLen;

			segStartDist += segLen;
		}

		// add one final stop, at the very end
		ProtoRouteStop prs = new ProtoRouteStop();
		prs.coord = coords[coords.length - 1];
		prs.routeId = routeId;
		prs.dist = totalLen;
		ret.add(prs);

		ret.length = totalLen;

		return ret;
	}

	private Integer getSpacing(ExtendedFeature feat) {
		Object spacingObj = data.get("spacing");
		if (Integer.class.isInstance(spacingObj)) {
			return (Integer) spacingObj;
		}

		JSONArray gtfsModeFilters = (JSONArray) spacingObj;

		for (int i = 0; i < gtfsModeFilters.length(); i++) {
			JSONArray gtfsModeFilter = gtfsModeFilters.getJSONArray(i);
			JSONArray filter = gtfsModeFilter.getJSONArray(0);
			Integer spacing = gtfsModeFilter.getInt(1);

			String propName = filter.getString(0);
			String propVal = filter.getString(1);

			if (propVal.equals("*")) { // star matches everything
				return spacing;
			}

			String featPropVal = feat.getProperty(propName);
			if (featPropVal != null && featPropVal.equals(propVal)) {
				return spacing;
			}
		}

		return null;
	}

	public ProtoRoute makeProtoRoute(ExtendedFeature exft, Double speed, String routeId) throws Exception {
		GeometryAttribute geomAttr = exft.feat.getDefaultGeometryProperty();
		MultiLineString geom = (MultiLineString) geomAttr.getValue();

		if (FAIL_ON_MULTILINESTRING && geom.getNumGeometries() > 1) {
			throw new Exception("Features may only contain a single linestring.");
		}
		
		double spacing = this.getSpacing(exft);

		LineString ls = (LineString) geom.getGeometryN(0);
		ProtoRoute ret = this.makeProtoRouteStopsFromLinestring(ls, spacing, routeId);
		ret.speed = speed;
		return ret;
	}

}
