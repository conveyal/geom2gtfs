package com.conveyal.geom2gtfs;

public class FeatureDoesntDefineTimeWindowException extends Exception {

	private static final long serialVersionUID = -5855030157442523986L;
	String propName;

	public FeatureDoesntDefineTimeWindowException(String propName) {
		this.propName = propName;
	}

}
