package com.conveyal.geom2gtfs;

import java.util.Map;

import org.opengis.feature.Feature;
import org.opengis.feature.Property;

/**
 * 
 * ExtendedFeature is a opengis Feature plus a map of additional properties.
 * Useful for doing data joins outside the shapefile.
 * 
 */
public class ExtendedFeature {

	private Map<String, String> extraFields;
	Feature feat;

	public ExtendedFeature(Feature feat, CsvJoinTable csvJoin) {
		this.feat = feat;
		if (csvJoin != null) {
			this.extraFields = csvJoin.getExtraFields(feat);
		} else {
			this.extraFields = null;
		}
	}

	/** get a raw property */
	public Object getPropertyRaw (String key) {
		if (extraFields != null) {
			String ret = extraFields.get(key);
			if (ret != null) {
				return ret;
			}
		}

		Property prop = feat.getProperty(key);
		if (prop == null) {
			return null;
		}
		Object val = prop.getValue();
		return val;
	}
	
	public String getProperty(String key) {
		Object val = getPropertyRaw(key);
		return val != null ? val.toString() : null;
	}

}
