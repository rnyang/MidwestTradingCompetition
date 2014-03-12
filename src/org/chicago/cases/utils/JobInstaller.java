package org.chicago.cases.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class JobInstaller {
	
	
	private static final String OUTPUT = "/home/bsandman/jobs/jobs.txt";
	private static final String DIR = "/home/bsandman/jobs";
	private static final int INSTANCES = 15;
	private static final String[] JOBS = new String[] {"ArbCaseRandom", "MathCaseRandom", "OptionCaseRandom"};
	
	public static void main(String[] args) {
		
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(OUTPUT));
			for (String job : JOBS) {
				for (int i = 0; i < INSTANCES; i++) {
					bw.write(job + " " + (i + 1));
					bw.newLine();
					File file = new File(DIR + "/vars." + job + "." + (i + 1) + ".txt");
					file.createNewFile();
				}
			}
			bw.flush();
			bw.close();
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
