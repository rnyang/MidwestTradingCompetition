package org.chicago.cases.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;

public class ArbCaseMD {
	
	private static final String INPUT = "/home/bsandman/arbcase.csv";
	private static final String OUTPUT = "/home/bsandman/arbcase-mod.csv";
	
	public static void main(String[] args) {
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(INPUT));
			BufferedWriter bw = new BufferedWriter(new FileWriter(OUTPUT));
			String line = br.readLine();
			while (line != null) {
				if (line.contains("BOOKUPDATE")) {
					String[] parts = line.split(",");
					//long time = Long.parseLong(parts[1]);
					String[] data = parts[2].split(";");
					//T,timestamp,symbol,bidSize,bidPrice,askSize,askPrice
					bw.write("T," + parts[1] + ",ROBOT-E,9999," + data[2] + ",9999," + data[3]);
					bw.newLine();
					bw.write("T," + parts[1] + ",SNOW-E,9999," + data[5] + ",9999," + data[6]);
					bw.newLine();
				}
				bw.write(line);
				bw.newLine();
				line = br.readLine();
			}
			bw.flush();
			bw.close();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
	}

}
