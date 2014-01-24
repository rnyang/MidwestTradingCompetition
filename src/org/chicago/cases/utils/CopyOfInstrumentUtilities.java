package org.chicago.cases.utils;

import java.util.ArrayList;
import java.util.List;

public class CopyOfInstrumentUtilities {
	
	public static List<String> MATH_SYMBOLS = new ArrayList<String>();	
	public static List<String> ARB_SYMBOLS = new ArrayList<String>();
	public static List<String> OPTIONS_SYMBOLS = new ArrayList<String>();
	public static List<String> OPTIONS_INSTRUMENTS = new ArrayList<String>();
	
	static {
		MATH_SYMBOLS.add("MATH");
		ARB_SYMBOLS.add("ARB");
		OPTIONS_SYMBOLS.add("RAND");
		
		OPTIONS_INSTRUMENTS.addAll(OPTIONS_SYMBOLS);
		OPTIONS_INSTRUMENTS.add("RAND-20140527-100C");
		OPTIONS_INSTRUMENTS.add("RAND-20140527-110C");
		OPTIONS_INSTRUMENTS.add("RAND-20140527-120C");
		OPTIONS_INSTRUMENTS.add("RAND-20140527-130C");
		OPTIONS_INSTRUMENTS.add("RAND-20140527-140C");
		OPTIONS_INSTRUMENTS.add("RAND-20140627-100C");
		OPTIONS_INSTRUMENTS.add("RAND-20140627-110C");
		OPTIONS_INSTRUMENTS.add("RAND-20140627-120C");
		OPTIONS_INSTRUMENTS.add("RAND-20140627-130C");
		OPTIONS_INSTRUMENTS.add("RAND-20140627-140C");
	}
	
	public static enum Case {	
		MATH(MATH_SYMBOLS, MATH_SYMBOLS),
		ARB(ARB_SYMBOLS, ARB_SYMBOLS),
		OPTIONS(OPTIONS_SYMBOLS, OPTIONS_INSTRUMENTS);
		
		private List<String> symbolList;
		private List<String> instrumentList;

		Case(List<String> symbolList, List<String> instrumentList) {
			this.symbolList = symbolList;
			this.instrumentList = instrumentList;
		}
		
		public List<String> getInstrumentList() {
			return instrumentList;
		}
		
		public List<String> getSymbolList() {
			return symbolList;
		}
	}
	
	public static List<String> getAllInstrumentsForTeam(String teamCode) {
		List<String> instruments = new ArrayList<String>();
		for (Case caze : Case.values()) {
			instruments.addAll(getInstrumentsForTeamByCase(caze, teamCode));
		}
		return instruments;
	}
	
	public static List<String> getAllInstrumentsForAllTeams() {
		List<String> instruments = new ArrayList<String>();
		for (String teamCode : TeamUtilities.TEAMS) {
			instruments.addAll(getAllInstrumentsForTeam(teamCode));
		}
		return instruments;
	}
	
	public static List<String> getInstrumentsForTeamByCase(Case caze, String teamCode) {
		List<String> instruments = new ArrayList<String>();
		if (TeamUtilities.validateTeamCode(teamCode)) {
			for (String instrument: caze.instrumentList)
				instruments.add(teamCode + "~" + instrument);
		}
		else {
			throw new IllegalArgumentException("Unknown team code");
		}
		return instruments;
	}
	
	public static List<String> getAllInstrumentsByCase(Case caze) {
		List<String> instruments = new ArrayList<String>();
		for (String teamCode : TeamUtilities.TEAMS) {
			for (String instrument: caze.instrumentList)
				instruments.add(teamCode + "~" + instrument);
		}
		return instruments;
	}
	
	public static List<String> getAllSymbolsForTeam(String teamCode) {
		List<String> symbols = new ArrayList<String>();
		for (Case caze : Case.values()) {
			symbols.addAll(getSymbolsForTeamByCase(caze, teamCode));
		}
		return symbols;
	}
	
	public static List<String> getAllSymbolsForAllTeams() {
		List<String> symbols = new ArrayList<String>();
		for (String teamCode : TeamUtilities.TEAMS) {
			symbols.addAll(getAllSymbolsForTeam(teamCode));
		}
		return symbols;
	}
	
	public static List<String> getSymbolsForTeamByCase(Case caze, String teamCode) {
		List<String> symbols = new ArrayList<String>();
		if (TeamUtilities.validateTeamCode(teamCode)) {
			for (String symbol: caze.symbolList)
				symbols.add(teamCode + "~" + symbol);
		}
		else {
			throw new IllegalArgumentException("Unknown team code");
		}
		return symbols;
	}
	
	public static List<String> getAllSymbolsByCase(Case caze) {
		List<String> symbols = new ArrayList<String>();
		for (String teamCode : TeamUtilities.TEAMS) {
			for (String symbol: caze.symbolList)
				symbols.add(teamCode + "~" + symbol);
		}
		return symbols;
	}
	
	
	
	
	

}
