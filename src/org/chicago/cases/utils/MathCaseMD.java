package org.chicago.cases.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

public class MathCaseMD {
	
	private static final String INPUT = "/home/bsandman/Downloads/mathcase.csv";
	private static final String OUTPUT = "/home/bsandman/mathcase.csv";
	
	public static void main(String[] args) {
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(INPUT));
			BufferedWriter bw = new BufferedWriter(new FileWriter(OUTPUT));
			String line = br.readLine();
			long previousTime = 0;
			int counter = 0;
			long time = 1000;
			long qty = 9999;
			while (line != null) {
				time += 5;
				if (line.contains("MIT3")) {
					counter += 1;
					qty -= 1;
				}
				String[] parts = line.split(",");
				
				parts[1] = "" + time;
				parts[3] = "" + qty;
				parts[5] = "" + qty;
				bw.write(buildLine(parts));
				bw.newLine();
				line = br.readLine();
			}
			System.out.println("Encountered " + counter + " events");
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
