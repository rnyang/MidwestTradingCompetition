package org.chicago.cases.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

public class CopyOfMathCaseMD {
	
	private static final String INPUT = "/home/bsandman/Downloads/mathcase.csv";
	private static final String OUTPUT = "/home/bsandman/Downloads/mathcase2.csv";
	
	public static void main(String[] args) {
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(INPUT));
			BufferedWriter bw = new BufferedWriter(new FileWriter(OUTPUT));
			String line = br.readLine();
			long previousTime = 0;
			int counter = 0;
			int conflict = 0;
			long time = 1000;
			String[] previousParts = null;
			while (line != null) {
				if (line.contains("MIT3")) {
					counter += 1;
					String[] parts = line.split(",");
					long currentTime = Long.parseLong(parts[1]);
					if (previousTime != 0) {
						//System.out.println(currentTime - previousTime);
					}
					if ((currentTime - previousTime) < 135) {
						System.out.println(line);
					}
					if (previousParts != null) {
						boolean bid = false;
						boolean ask = false;
						if (previousParts[4].equals(parts[4])) {
							System.out.println("Bid not changed");
							bid = true;
						}
						if (previousParts[4].equals(parts[4])) {
							System.out.println("Bid not changed");
							ask = true;
						}
						if (bid && ask) {
							conflict += 1;
						}
					}
					previousParts = parts;
					previousTime = currentTime;
				}
				line = br.readLine();
			}
			System.out.println("Encountered " + counter + " events");
			System.out.println("Encountered " + conflict + " conflicts");
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
