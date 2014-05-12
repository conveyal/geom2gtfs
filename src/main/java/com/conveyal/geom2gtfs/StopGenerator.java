package com.conveyal.geom2gtfs;

public interface StopGenerator {

	ProtoRoute makeProtoRoute(ExtendedFeature exft, Double speed, String routeId) throws Exception;
	
}
