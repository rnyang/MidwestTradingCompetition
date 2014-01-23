

import org.chicago.cases.utils.TeamUtilities;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IJobSetup;

/*
 * Likely not of use to students.  This job will forcibly pull the team products from the CX
 * implementation.  Without, the request for instrument definition will not proceed.
 */

public class InstrumentDownloaderJob extends AbstractJob {

	public void install(IJobSetup arg0) {}
	
	public void begin(IContainer container) {
		super.begin(container);
		for (String team : TeamUtilities.TEAMS) {

			// C1 (arb) symbols
			instruments().startSymbol(team + "-CASE1");

			// C2 (options) symbols
			instruments().startSymbol(team + "-CASE2");

			// C3 (math) - symbols
			instruments().startSymbol(team + "-CASE3");
		}
		
	}
	
}
