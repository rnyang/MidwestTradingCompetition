package org.chicago.cases.math;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;

import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.PriceGenerator;
import org.chicago.cases.utils.InstrumentUtilities.Case;

public class MathCaseMarketDataGenerator {
	
	private static final String FILE_PATH = "/home/bsandman/options.csv";
	private static final int DATA_POINTS = 100;
	private static final long INTERVAL = 1000;
	private static final int QUANTITY = 100;
	
	public static void main(String[] args) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH));
			
			List<String> allTeamsUnderlyings = InstrumentUtilities.getUnderlyingsForCase(Case.MATH);
			PriceGenerator prices = new PriceGenerator(100, 80, 120, 2, 1);
			
			long time = System.currentTimeMillis();
			for (int i = 0; i < DATA_POINTS; i++) {
				
				// Get next quote [bid,ask]
				double[] quote = prices.getNextPrices();
				
				// For each team (which has a different underlying symbol), write the price out to the file
				for (String underlying : allTeamsUnderlyings) {
					// T,time,symbol,bidQuantity,bidPrice,askQuantity,askPrice
					// Note: Quantity does not matter here - in reality, it's infinite
					bw.write("T," + time + "," + underlying + "," + QUANTITY + "," + quote[0] + "," + QUANTITY + "," + quote[1]);
					bw.newLine();
				}
				time += INTERVAL;
			}
			bw.flush();
			bw.close();
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

}
