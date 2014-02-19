package org.chicago.cases.marketdata;

import org.chicago.cases.utils.TeamUtilities;

public class TeamLister {

	public static void main(String[] args) {

		try {
			for (String team : TeamUtilities.TEAMS) {
				System.out.println(team);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
	}

}

