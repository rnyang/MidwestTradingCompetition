package org.chicago.cases.marketdata;

import java.io.BufferedWriter;
import java.io.FileWriter;

import org.chicago.cases.utils.TeamUtilities;

public class MarketDataGenerator {
	
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
			BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH));
			
			// Set upper and lower price bounds of midpoint within file
			double topPrice = START_PRICE + (TICK_RANGE * TICK_SIZE);
			double bottomPrice = START_PRICE - (TICK_RANGE * TICK_SIZE);
			
			// Initial state
			double currentPrice = START_PRICE;
			double midpointIncrement = TICK_SIZE;
			long timeIndex = System.currentTimeMillis(); // we're using Unix timestamp as time within file
			
			for (int i = 0; i < DATA_POINTS; i++) {
				
				// If midpoint is touching bounds, reverse direction
				if (currentPrice >= topPrice)
					midpointIncrement = -TICK_SIZE;
				else if (currentPrice <= bottomPrice)
					midpointIncrement = TICK_SIZE;
				
				// Write out market data entry to file - assume quantity of 10000
				double currentBid = currentPrice - (TICK_SPREAD * TICK_SIZE);
				double currentAsk = currentPrice + (TICK_SPREAD * TICK_SIZE);
				for (String team : TeamUtilities.TEAMS) {
					bw.write("T," + timeIndex + "," + team + "-CASE3-E" + "," + DEFAULT_QUANTITY + "," + currentBid + "," + DEFAULT_QUANTITY + "," + currentAsk);
					bw.newLine();
				}
				
				currentPrice += midpointIncrement;
				timeIndex += INTERVAL;
			}
			
			bw.flush();
			bw.close();
			
		} catch (Throwable e) {
			// If you've hit this, make sure you've changed the FILE_PATH to reference your system
			e.printStackTrace();
		}
	}

}
