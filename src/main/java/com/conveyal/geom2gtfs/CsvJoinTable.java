package com.conveyal.geom2gtfs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opengis.feature.Feature;

public class CsvJoinTable {

	private String csvCol;
	private String shpCol;
	private Map<String, String[]> records;
	private ArrayList<String> header;

	public CsvJoinTable(String filename, String csvCol, String shpCol) throws IOException {
		this.csvCol = csvCol;
		this.shpCol = shpCol;
		this.records = new HashMap<String, String[]>();
		
		BufferedReader br = new BufferedReader(new FileReader(new File(filename)));
		String headerStr = br.readLine();
		header = new ArrayList<String>( Arrays.asList( headerStr.split(",") ) );
		
		int keyCol = header.indexOf(this.csvCol);
		
		String line;
		while ((line = br.readLine()) != null) {
		   String[] row = line.split(",");
		   String key = row[keyCol];
		   records.put(key, row);
		}
		br.close();
	}

	public Map<String,String> getExtraFields(Feature feat) {
		Map<String,String> ret = new HashMap<String,String>();
		
		String key = feat.getProperty(shpCol).getValue().toString();
		String[] fields = records.get(key);
		if( fields==null ){
			return ret;
		}
		
		for(int i=0; i<header.size(); i++){
			String kk = header.get(i);
			String vv = fields[i];
			ret.put(kk, vv);
		}
		return ret;
	}

}
