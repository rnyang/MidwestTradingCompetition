package org.chicago.cases.teams;

public class InstrumentUtilities {
	
	public static final String[] INSTRUMENTS = new String[] {
		"MATH-E",
		"ARB-E",
		"RAND-E",
		"RAND-20140527-100C",
		"RAND-20140527-110C",
		"RAND-20140527-120C",
		"RAND-20140527-130C",
		"RAND-20140527-140C",
		"RAND-20140627-100C",
		"RAND-20140627-110C",
		"RAND-20140627-120C",
		"RAND-20140627-130C",
		"RAND-20140627-140C",
	};
	
	public static String[] getInstrumentsForTeam(String teamCode) {
		String[] teamInstruments = new String[INSTRUMENTS.length];
		if (TeamUtilities.validateTeamCode(teamCode)) {
			for (int i = 0; i < INSTRUMENTS.length; i++)
				teamInstruments[i] = teamCode + "~" + INSTRUMENTS[i];
		}
		else {
			throw new IllegalArgumentException("Unknown team code");
		}
		return teamInstruments;
	}
	
	public static String[] getInstrumentsForAllTeams() {
		String[] teams = TeamUtilities.TEAMS;
		String[] allInstruments = new String[INSTRUMENTS.length * teams.length];
		for (int i = 0; i < teams.length; i++) {
			String teamCode = teams[i];
			for (int j = 0; j < INSTRUMENTS.length; j++) {
				allInstruments[(i * INSTRUMENTS.length) + j] = teamCode + "~" + INSTRUMENTS[j];
			}
		}
		return allInstruments;
	}
	
	

}
