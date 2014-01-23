package org.chicago.cases.arb;

import java.io.BufferedWriter;
import java.io.FileWriter;

import org.chicago.cases.utils.PriceGenerator;

public class ArbCaseMarketDataGenerator {
	
	private static final String FILE_PATH = "/home/bsandman/arb.csv";
	private static final int DATA_POINTS = 100;
	private static final long INTERVAL = 1000;
	private static final int QUANTITY = 100;
	
	public static void main(String[] args) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH));
			
			PriceGenerator robot = new PriceGenerator(100, 80, 120, 2, 1);
			PriceGenerator snow = new PriceGenerator(102, 78, 125, 2, 1);
			
			long time = System.currentTimeMillis();
			for (int i = 0; i < DATA_POINTS; i++) {
				
				// Get next quote [bid,ask]
				double[] robotQuote = robot.getNextPrices();
				double[] snowQuote = snow.getNextPrices();
				
			    // Write the price out to the file
				bw.write("S," + time + ",ROBOT;" + robotQuote[0] + ";" + robotQuote[1] + ";SNOW;" + snowQuote[0] + ";" + snowQuote[1]);
				bw.newLine();
				time += INTERVAL;
				
			}
			bw.flush();
			bw.close();
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

}
