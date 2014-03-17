import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Date;
import java.util.List;

import org.chicago.cases.utils.TeamUtilities;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.PositionRisk;


public class DatabaseDumpJob extends AbstractJob {
	
	private static IDB database;
	private static IDB pnl;
	private static int ITERATION = 0;

	public void install(IJobSetup setup) {
		setup.setVariable("timer", "2500");
		setup.addVariable("fileNameBase", "File Name", "string", "dump");
	}

	@Override
	public void begin(IContainer container) {
		super.begin(container);
		ITERATION += 1;
		database = container.getDB("trades");
		pnl = container.getDB("pnl");
	}

	@Override
	public void onTimer() {
		String fileName = getStringVar("fileNameBase");
		boolean success = false;
		try {
			long time = System.currentTimeMillis();
			BufferedWriter bw = new BufferedWriter(new FileWriter("jobfiles/" + fileName + "." + ITERATION + "." + time));
			for (String team : TeamUtilities.TEAMS) {
				String output = (String)pnl.get(team);
				if (output == null) {
					output = "NO RESULTS";
				}
				bw.write(team + "," + output);
				bw.newLine();
			}
			bw.flush();
			/*
			List<String> allData = (List<String>)pnl.get("ALL");
			bw.write("----------- ALL ------------");
			for (String data : allData) {
				bw.write(data);
				bw.newLine();
			}
			bw.flush();
			*/
			bw.close();
		} catch (Throwable e) {
			log("Unexpected exception, " + e.toString());
		}
		container.stopJob("Job killed");
	}


}
