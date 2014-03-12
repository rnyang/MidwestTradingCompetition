package org.chicago.cases.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

public class CopyOfDataReader {
	
	private static final String INPUT = "/home/bsandman/Downloads/data-backup/math_one.csv";
	private static final String OUTPUT = "/home/bsandman/Downloads/data/math_one.csv";
	
	public static void main(String[] args) {
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(INPUT));
			BufferedWriter bw = new BufferedWriter(new FileWriter(OUTPUT));
			String line = br.readLine();
			while (line != null) {
				String[] parts = line.split(",");
				long time = Long.parseLong(parts[1]);
				parts[1] = "" + time;
				System.out.println(time);
				bw.write(buildLine(parts));
				bw.newLine();
				line = br.readLine();
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
