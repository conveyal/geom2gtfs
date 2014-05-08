package com.conveyal.geom2gtfs;

public class ServiceWindow {

	private int start;
	private int end;
	public String propName;
	public int startSecs() {
		return start*3600;
	}
	public int endSecs(){
		return end*3600;
	}
	public void setStartHour(int startHour) {
		this.start = startHour;
	}
	public void setEndHour(int endHour){
		this.end = endHour;
	}

}
