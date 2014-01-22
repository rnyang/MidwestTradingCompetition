package org.chicago.cases.teams;

public class TeamUtilities {
	
	public static final String[] TEAMS = new String[] { "ILL1", "ILL2", "CMU1",
		"CMU2", "CMU3", "BAR1", "WAT1", "RHU1", "MIT1", "MIT2", "DAR1",
		"MIC1", "CHI1", "CHI2", "CHI3", "CHI4", "CAL1", "CAL2", "MIX1",
		"COR1", "HA1", "UCB1", "IOW1" };
	
	public static boolean validateTeamCode(String teamCode) {
		for (String team : TEAMS) {
			if (team.equals(teamCode))
				return true;
		}
		return false;
	}
	
	

}
