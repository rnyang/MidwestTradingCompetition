package org.chicago.cases.marketdata;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;

import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.TeamUtilities;

public class TeamLister {
	
	private static final String FILE_PATH = "/home/bsandman/instruments.csv";
	
	public static void main(String[] args) {
		
		try {
			for (String team : TeamUtilities.TEAMS) {
				System.out.println(team);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
	}

}
 