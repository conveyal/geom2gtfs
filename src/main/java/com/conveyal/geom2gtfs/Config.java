package com.conveyal.geom2gtfs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.feature.Feature;

public class Config {
	
	JSONObject data; 

	public Config(String config_fn) throws IOException {
		File ff = new File(config_fn);
		String jsonStr = new String( Files.readAllBytes( ff.toPath() ) );
		data = new JSONObject( jsonStr );
	}

	public String getAgencyName() {
		try{
			return data.getString("agency_name");
		}catch(JSONException e){
			return null;
		}		
	}

	public String getAgencyUrl() {
		try{
			return data.getString("agency_url");
		}catch(JSONException e){
			return null;
		}
	}

	public String getAgencyTimezone() {
		try{
			return data.getString("agency_timezone");
		}catch(JSONException e){
			return null;
		}
	}

	public Integer getMode(Feature feat) {
		JSONArray gtfsModeFilters = data.getJSONArray("gtfs_mode");
		
		for(int i=0; i<gtfsModeFilters.length(); i++){
			JSONArray gtfsModeFilter = gtfsModeFilters.getJSONArray(i);
			JSONArray filter = gtfsModeFilter.getJSONArray(0);
			Integer mode = gtfsModeFilter.getInt(1);
			
			String propName = filter.getString(0);
			String propVal = filter.getString(1);
			
			if(propVal.equals("*")){ //star matches everything
				return mode;
			}
			
			if(feat.getProperty(propName).getValue().toString().equals(propVal)){
				return mode;
			}
		}
		
		return null;
	}

	public Integer getSpacing(Feature feat) {
		JSONArray gtfsModeFilters = data.getJSONArray("spacing");
		
		for(int i=0; i<gtfsModeFilters.length(); i++){
			JSONArray gtfsModeFilter = gtfsModeFilters.getJSONArray(i);
			JSONArray filter = gtfsModeFilter.getJSONArray(0);
			Integer spacing = gtfsModeFilter.getInt(1);
			
			String propName = filter.getString(0);
			String propVal = filter.getString(1);
			
			if(propVal.equals("*")){ //star matches everything
				return spacing;
			}
			
			if(feat.getProperty(propName).getValue().toString().equals(propVal)){
				return spacing;
			}
		}
		
		return null;
	}

	public Double getSpeed(Feature feat) {
		JSONArray gtfsModeFilters = data.getJSONArray("speed");
		
		for(int i=0; i<gtfsModeFilters.length(); i++){
			JSONArray gtfsModeFilter = gtfsModeFilters.getJSONArray(i);
			JSONArray filter = gtfsModeFilter.getJSONArray(0);
			Double speed = gtfsModeFilter.getDouble(1);
			
			String propName = filter.getString(0);
			String propVal = filter.getString(1);
			
			if(propVal.equals("*")){ //star matches everything
				return speed;
			}
			
			if(feat.getProperty(propName).getValue().toString().equals(propVal)){
				return speed;
			}
		}
		
		return null;
	}

	public String getRouteIdPropName() {
		try{
			return data.getString("route_id_prop_name");
		}catch(JSONException e){
			return null;
		}
	}

	public String getRouteNamePropName() {
		try{
			return data.getString("route_name_prop_name");
		}catch(JSONException e){
			return null;
		}
	}

	public boolean isBidirectional() {
		try{
			return data.getBoolean("is_bidirectional");
		}catch(JSONException e){
			return false;
		}
	}

	public List<ServiceWindow> getServiceWindows() {
		List<ServiceWindow> ret = new ArrayList<ServiceWindow>();
		
		JSONArray windows = data.getJSONArray("service_windows");
		for(int i=0; i<windows.length(); i++){
			JSONArray jWindow = windows.getJSONArray(i);
			
			ServiceWindow sw = new ServiceWindow();
			sw.propName = jWindow.getString(0);
			sw.start = jWindow.getInt(1);
			sw.end = jWindow.getInt(2);
			
			ret.add(sw);
		}
		
		return ret;
	}

}
