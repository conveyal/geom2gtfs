package com.conveyal.geom2gtfs;

import java.util.Map;

import org.opengis.feature.Feature;
import org.opengis.feature.Property;

public class ExtendedFeature {

	private Map<String, String> extraFields;
	Feature feat;

	public ExtendedFeature(Feature feat, CsvJoinTable csvJoin) {
		this.feat = feat;
		this.extraFields = csvJoin.getExtraFields( feat );
	}

	public String getProperty(String key) {
		String ret = extraFields.get(key);
		if(ret==null){
			Property prop = feat.getProperty(key);
			if(prop==null){
				return null;
			}
			ret = prop.getValue().toString();
		}
		return ret;
	}

}
