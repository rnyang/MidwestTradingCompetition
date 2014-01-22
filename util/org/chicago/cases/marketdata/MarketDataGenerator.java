package org.chicago.cases.marketdata;

import java.io.BufferedWriter;
import java.io.FileWriter;

import org.chicago.cases.teams.TeamUtilities;

public class MarketDataGenerator {
	
	private static final String FILE_PATH = "/home/bsandman/data.csv";
	private static final double START_PRICE = 100;
	private static final int TICK_RANGE = 5;
	private static final double TICK_INCREMENT = 1;
	private static final long INTERVAL = 500;
	private static final int ITERATIONS = 100;
	private static final int TICK_SPREAD = 1;
	
	public static void main(String[] args) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH));
			
			
			double topPrice = START_PRICE + (TICK_RANGE * TICK_INCREMENT);
			double bottomPrice = START_PRICE - (TICK_RANGE * TICK_INCREMENT);
			
			double currentPrice = START_PRICE;
			double increment = TICK_INCREMENT;
			
			long timeIndex = System.currentTimeMillis();
			for (int i = 0; i < ITERATIONS; i++) {
				if (currentPrice >= topPrice)
					increment = -TICK_INCREMENT;
				else if (currentPrice <= bottomPrice)
					increment = TICK_INCREMENT;
				
				// Write out market data entry to file - assume quantity of 10000
				double currentBid = currentPrice - (TICK_SPREAD * TICK_INCREMENT);
				double currentAsk = currentPrice + (TICK_SPREAD * TICK_INCREMENT);
				for (String team : TeamUtilities.TEAMS) {
					bw.write("T," + timeIndex + "," + team + "-CASE3-E" + ",10000," + currentBid + ",10000," + currentAsk);
					bw.newLine();
				}
				
				currentPrice += increment;
				timeIndex += INTERVAL;
			}
			
			bw.flush();
			bw.close();
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

}
