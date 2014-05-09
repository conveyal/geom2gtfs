package com.conveyal.geom2gtfs;

import java.util.ArrayList;
import java.util.List;

public class ProtoRoute {
	List<ProtoRouteStop> ret = new ArrayList<ProtoRouteStop>();
	public double length;
	public double speed;

	public void add(ProtoRouteStop prs) {
		ret.add(prs);
	}

	public double getDuration() {
		return length/speed;
	}
}
