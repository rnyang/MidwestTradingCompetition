package org.chicago.cases.options;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;

import org.chicago.cases.utils.InstrumentUtilities;
import org.chicago.cases.utils.InstrumentUtilities.Case;

public class OptionsCaseMarketDataGenerator {
	
	private static final String FILE_PATH = "/home/bsandman/options.csv";
	
	public static void main(String[] args) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH));
			
			List<String> underlyings = InstrumentUtilities.getUnderlyingsForCase(Case.OPTIONS);
			List<String> options = InstrumentUtilities.getOptionsForCase(Case.OPTIONS);
			
			
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	

}
