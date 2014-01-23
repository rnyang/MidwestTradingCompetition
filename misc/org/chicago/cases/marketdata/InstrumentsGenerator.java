package org.chicago.cases.marketdata;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;

import org.chicago.cases.utils.InstrumentUtilities;

public class InstrumentsGenerator {
	
	private static final String FILE_PATH = "/home/bsandman/instruments.csv";
	
	public static void main(String[] args) {
		
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(FILE_PATH));
			List<String> instruments = InstrumentUtilities.getAllInstrumentsForAllTeams();
			for (String instrument : instruments) {
				bw.write(instrument + ",0.01");
				bw.newLine();
			}
			bw.flush();
			bw.close();
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
	}

}
 