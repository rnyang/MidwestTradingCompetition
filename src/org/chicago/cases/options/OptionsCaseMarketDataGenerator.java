package org.chicago.cases.options;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;
import java.util.Random;

import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.InstrumentUtilities.Case;
import org.chicago.cases.utils.PriceGenerator;

public class OptionsCaseMarketDataGenerator {
	
	private static final String FILE_PATH = "/home/bsandman/options.csv";
	private static final int DATA_POINTS = 100;
	private static final long INTERVAL = 500;
	private static final int QUANTITY = 100;
	
	public static void main(String[] args) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH));
			
			List<String> allTeamsUnderlyings = InstrumentUtilities.getUnderlyingsForCase(Case.OPTIONS);
			List<String> allTeamsOptions = InstrumentUtilities.getOptionsForCase(Case.OPTIONS);
			
			PriceGenerator underlyingPrices = new PriceGenerator(20, 15, 25, 1, 1);
			PriceGenerator optionPrices = new PriceGenerator(20, 17, 25, 1, 1);
			
			Random random = new Random();
			
			long time = System.currentTimeMillis();
			for (int i = 0; i < DATA_POINTS; i++) {
				
				// Get next quote [bid,ask]
				double[] underlyingQuote = underlyingPrices.getNextPrices();
				double[] optionsQuote = optionPrices.getNextPrices();
				
				// For each team (which has a different underlying symbol), write the price out to the file
				for (String underlying : allTeamsUnderlyings) {
					// T,time,symbol,bidQuantity,bidPrice,askQuantity,askPrice
					// Note: Quantity does not matter here - in reality, it's infinite
					bw.write("T," + time + "," + underlying + "," + QUANTITY + "," + underlyingQuote[0] + "," + QUANTITY + "," + underlyingQuote[1]);
					bw.newLine();
				}
				time += INTERVAL;
				for (String option : allTeamsOptions) {
					// T,time,symbol,bidQuantity,bidPrice,askQuantity,askPrice
					// Note: Quantity does not matter here - in reality, it's infinite
					bw.write("T," + time + "," + option + "," + QUANTITY + "," + optionsQuote[0] + "," + QUANTITY + "," + optionsQuote[1]);
					bw.newLine();
				}
				time += INTERVAL;
					
				if ((i % 10) == 0) {
					int magnitude = random.nextInt(100);
					bw.write("S," + time + ",admin;VEGA;" + magnitude);
					bw.newLine();
				}
				if ((i % 15) == 0) {
					int magnitude = random.nextInt(100);
					bw.write("S," + time + ",forecast;VEGA;" + magnitude);
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
