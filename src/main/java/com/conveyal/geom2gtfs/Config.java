package com.conveyal.geom2gtfs;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.feature.Feature;

public class Config {
	
	JSONObject data;
	private boolean DEFAULT_USE_PERIODS = false;
	private double DEFAULT_WAIT_FACTOR = 1.0;
	private boolean DEFAULT_EXACT = false; 
	private static final boolean DEFAULT_TOLERANT = true;

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

	public Integer getMode(ExtendedFeature feat) {
		Object modeObj = data.get("gtfs_mode");
		if( Integer.class.isInstance( modeObj) ){
			return (Integer)modeObj;
		}
		
		// else it should be an array;
		JSONArray gtfsModeFilters = (JSONArray)modeObj;
		
		for(int i=0; i<gtfsModeFilters.length(); i++){
			JSONArray gtfsModeFilter = gtfsModeFilters.getJSONArray(i);
			JSONArray filter = gtfsModeFilter.getJSONArray(0);
			Integer mode = gtfsModeFilter.getInt(1);
			
			String propName = filter.getString(0);
			String propVal = filter.getString(1);
			
			if(propVal.equals("*")){ //star matches everything
				return mode;
			}
			
			String featPropVal = feat.getProperty(propName);
			if(featPropVal!=null && featPropVal.equals(propVal)){
				return mode;
			}
		}
		
		return null;
	}
	
	public StopGenerator getStopGenerator() throws MalformedURLException, IOException{
		JSONObject stopGeneratorData;
		
		try{
			stopGeneratorData = data.getJSONObject("stops");
		} catch (JSONException ex){
			return null;
		}
		
		String strategy = stopGeneratorData.getString("strategy");
		
		if(strategy.equals("picket")){
			return new PicketStopGenerator( stopGeneratorData );
		} else if(strategy.equals("shapefile")){
			return new ShapefileStopGenerator( stopGeneratorData );
		} else if(strategy.equals("cluster")){
		    return new ClusterStopGenerator(stopGeneratorData);
		} else {
			throw new UnsupportedOperationException( "no stop generator corresponds to strategy '"+strategy+"'" );
		}
		
	}

//	public Integer getSpacing(ExtendedFeature feat) {
//		Object spacingObj = data.get("spacing");
//		if(Integer.class.isInstance(spacingObj)){
//			return (Integer)spacingObj;
//		}
//		
//		JSONArray gtfsModeFilters = (JSONArray)spacingObj;
//		
//		for(int i=0; i<gtfsModeFilters.length(); i++){
//			JSONArray gtfsModeFilter = gtfsModeFilters.getJSONArray(i);
//			JSONArray filter = gtfsModeFilter.getJSONArray(0);
//			Integer spacing = gtfsModeFilter.getInt(1);
//			
//			String propName = filter.getString(0);
//			String propVal = filter.getString(1);
//			
//			if(propVal.equals("*")){ //star matches everything
//				return spacing;
//			}
//			
//			String featPropVal = feat.getProperty(propName);
//			if(featPropVal!=null && featPropVal.equals(propVal)){
//				return spacing;
//			}
//		}
//		
//		return null;
//	}

	public Double getSpeed(ExtendedFeature feat) {
		Object speedObj = data.get("speed");
		if(Double.class.isInstance(speedObj)){
			return (Double)speedObj;
		}
		
		JSONArray gtfsModeFilters = (JSONArray)speedObj;
		
		for(int i=0; i<gtfsModeFilters.length(); i++){
			JSONArray gtfsModeFilter = gtfsModeFilters.getJSONArray(i);
			JSONArray filter = gtfsModeFilter.getJSONArray(0);
			Double speed = gtfsModeFilter.getDouble(1);
			
			String propName = filter.getString(0);
			String propVal = filter.getString(1);
			
			if(propVal.equals("*")){ //star matches everything
				return speed;
			}
			
			String featPropVal = feat.getProperty(propName);
			if(featPropVal!=null && featPropVal.equals(propVal)){
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
			sw.setStartHour( jWindow.getInt(1) );
			sw.setEndHour( jWindow.getInt(2) );
			
			ret.add(sw);
		}
		
		return ret;
	}

	public Date getStartDate() {
		String startDateStr = data.getString("start_date");
		Calendar ret = Calendar.getInstance();
		ret.set(Calendar.YEAR, Integer.parseInt( startDateStr.substring(0, 4) ));
		ret.set(Calendar.MONTH, Integer.parseInt( startDateStr.substring(4,6) ));
		ret.set(Calendar.DAY_OF_MONTH, Integer.parseInt( startDateStr.substring(6) ));
		return ret.getTime();
	}

	public Date getEndDate() {
		String startDateStr = data.getString("end_date");
		Calendar ret = Calendar.getInstance();
		ret.set(Calendar.YEAR, Integer.parseInt( startDateStr.substring(0, 4) ));
		ret.set(Calendar.MONTH, Integer.parseInt( startDateStr.substring(4,6) ));
		ret.set(Calendar.DAY_OF_MONTH, Integer.parseInt( startDateStr.substring(6) ));
		return ret.getTime();
	}

	public CsvJoinTable getCsvJoin() throws IOException {
		try{
			JSONObject obj = data.getJSONObject("csv_join");
			String filename = obj.getString("filename");
			String csvCol = obj.getString( "csv_col" );
			String shpCol = obj.getString( "shp_col" );
			
			return new CsvJoinTable( filename, csvCol, shpCol );
		} catch( JSONException ex ){
			return null;
		}
	}

	public boolean usePeriods() {
		try{
			return data.getBoolean("use_periods");
		} catch (JSONException ex){
			return DEFAULT_USE_PERIODS;
		}
	}

	public boolean passesFilter(ExtendedFeature exft) {
		JSONArray filters;
		try{
			filters = data.getJSONArray("filters");
		} catch (JSONException ex){
			return true;
		}
		
		for(int i=0; i<filters.length(); i++){
			JSONArray filter = filters.getJSONArray(i);
			String propName = filter.getString(0);
			String acceptable = filter.getString(1);
			
			String val = exft.getProperty(propName);
			if(!val.equals(acceptable)){
				return false;
			}
		}
		
		return true;
	}

	public double waitFactor() {
		try{
			return data.getDouble("wait_factor");
		} catch (JSONException ex){
			return DEFAULT_WAIT_FACTOR  ;
		}
	}

	public boolean isExact() {
		try{
			return data.getBoolean("exact");
		} catch (JSONException ex){
			return DEFAULT_EXACT   ;
		}
	}

	public boolean tolerant() {
		try{
			return data.getBoolean("tolerant");
		} catch (JSONException ex){
			return DEFAULT_TOLERANT   ;
		}
	}
	
	
	/**
	 * Get the desired stop spacing for feature feat from the stop generator
	 * configuration stopGeneratorData.
	 */
	public static Integer getSpacing(ExtendedFeature feat, JSONObject stopGeneratorData) {
	    Object spacingObj = stopGeneratorData.get("spacing");
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
}
