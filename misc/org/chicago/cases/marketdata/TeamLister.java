package org.chicago.cases.marketdata;

import java.io.BufferedWriter;
import java.io.FileWriter;

import org.chicago.cases.utils.TeamUtilities;

public class TeamLister {
	
	private static final String FILE_PATH = "/home/bsandman/data.csv";
	private static final double START_PRICE = 100;
	private static final int TICK_RANGE = 5;
	private static final double TICK_SIZE = 1;
	private static final long INTERVAL = 500;
	private static final int DATA_POINTS = 100;
	private static final int TICK_SPREAD = 1;
	private static final int DEFAULT_QUANTITY = 10000;
	
	public static void main(String[] args) {
		try {
			for (String team : TeamUtilities.TEAMS) {
				System.out.println(team);
			}
			
			
		} catch (Throwable e) {
			// If you've hit this, make sure you've changed the FILE_PATH to reference your system
			e.printStackTrace();
		}
	}

}
