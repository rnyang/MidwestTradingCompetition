package org.chicago.cases.utils;

public class TeamUtilities {
	
	public static final String[] TEAMS = new String[] { "ILL1", "ILL2", "CMU1",
		"CMU2", "CMU3", "BAR1", "WAT1", "RHU1", "MIC1", "MIC2", "MIT1", "MIT2", "MIT3", "DAR1",
		"CHI1", "CHI2", "CHI3", "CHI4", "CHI5", "CHI6", "CAL1", "CAL2", "MIX1",
		"COR1", "HAR1", "UCB1", "IOW1" };
	
	public static boolean validateTeamCode(String teamCode) {
		for (String team : TEAMS) {
			if (team.equals(teamCode))
				return true;
		}
		return false;
	}
	
	

}
