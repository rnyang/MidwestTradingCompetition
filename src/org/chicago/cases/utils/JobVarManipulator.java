package org.chicago.cases.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

public class JobVarManipulator {
	
	private static final String INPUT = "/home/bsandman/jobs-template/vars.MathCaseRandom.1.txt";
	private static final String JOB = "OptionCaseRandom";
	private static final int INSTANCES = 15;
	private static final String DIR = "/home/bsandman/jobs";
	
	
	
	public static void main(String[] args) {
		
		try {
			String[] teams = TeamUtilities.TEAMS;
			for (int i = 0; i < INSTANCES; i++) {
				BufferedReader br = new BufferedReader(new FileReader(INPUT));
				String file = DIR + "/vars." + JOB + "." + (i + 1) + ".txt";
				BufferedWriter bw = new BufferedWriter(new FileWriter(file));
				
				String line = br.readLine();

				while (line != null) {
					String[] parts = line.split(",");
					if (line.contains("Team_Code")) {
						line = "Team_Code=" + teams[i];
					}
					if (line.contains("group")) {
						line = "group=" + "OPTIONS";
					}
					if (line.contains("timer")) {
						line = "timer=" + "500";
					}
					bw.write(line);
					bw.newLine();
					line = br.readLine();
				}
				bw.flush();
				bw.close();
			}
			
			
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
	}
	
	public static String buildLine(String[] parts) {
		String result = "";
		for (String part : parts) {
			result += "," + part;
		}
		return result.substring(1);
	}

}
