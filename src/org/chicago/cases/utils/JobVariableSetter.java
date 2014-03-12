package org.chicago.cases.utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class JobVariableSetter {
	
	private static final String DIR = "/home/bsandman/jobs-backup";
	private static final String PATH = "/home/bsandman/jobs";
	private static List<String> mathTeams = new ArrayList<String>();
	private static List<String> optionTeams = new ArrayList<String>();
	private static List<String> arbTeams = new ArrayList<String>();
	
	public static void main(String[] args) {
		BufferedWriter bw = null;
		try {
			
			
			File dir = new File(DIR);
			for (File file : dir.listFiles()) {
				if (!file.getName().contains("vars"))
					continue;
				BufferedReader br = new BufferedReader(new FileReader(file));
				bw = new BufferedWriter(new FileWriter(PATH + "/" + file.getName()));
				
				String line = br.readLine();

				while (line != null) {
					String teamCode = findTeam(file.getName());
					if (teamCode == null)
						throw new IllegalStateException(file + " did not have team");
					
					if (line.contains("Team_Code")) {
						line = "Team_Code=" + teamCode;
					}
					String group = findGroup(file.getName());
					if (group == null)
						throw new IllegalStateException(file + " did not have group");
					
					if (group.toLowerCase().contains("option")) {
						optionTeams.add(teamCode);
					}
					else if (group.toLowerCase().contains("math")) {
						mathTeams.add(teamCode);
					}
					else if (group.toLowerCase().contains("arb")) {
						arbTeams.add(teamCode);
					}
					
					if (line.contains("group")) {
						line = "group=" + group;
					}
					if (line.contains("timer")) {
						line = "timer=" + "500";
					}
					bw.write(line);
					bw.newLine();
					line = br.readLine();
				}
				
				bw.flush();
				bw.close();
			}
			
			for (String team : TeamUtilities.TEAMS) {
				if (!mathTeams.contains(team)) {
					System.out.println("Math cases did not include " + team);
				}
				if (!optionTeams.contains(team)) {
					System.out.println("Option cases did not include " + team);
				}
				if (!arbTeams.contains(team)) {
					System.out.println("Arb cases did not include " + team);
				}
			}
			
		} catch (Throwable e) {
			if (bw != null)
				try {
					bw.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			e.printStackTrace();
		}
		
	}
	
	private static String findGroup(String name) {
		if (name.toLowerCase().contains("opt")) {
			return "Option";
		}
		else if (name.toLowerCase().contains("math")) {
			return "Math";
		}
		else if (name.toLowerCase().contains("arb")) {
			return "Arb";
		}
		else {
			return null;
		}
	}

	private static String findTeam(String name) {
		String[] teams = TeamUtilities.TEAMS;
		for (String team : teams) {
			if (name.toLowerCase().contains(team.toLowerCase()))
				return team;
		}
		return null;
	}

	public static String buildLine(String[] parts) {
		String result = "";
		for (String part : parts) {
			result += "," + part;
		}
		return result.substring(1);
	}

}
