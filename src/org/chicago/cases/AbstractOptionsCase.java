package org.chicago.cases;

import java.util.Map;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public abstract class AbstractOptionsCase extends AbstractJob {
	
	// ---------------- Define Case Interface and abstract method ----------------
		/*
		 * By having the abstract method: getMathCaseImplementation(), we are saying, "All classes that
		 * extend AbstractMathCase must return their implementation of the MathCase interface".
		 * 
		 * When the algo (we call algos "jobs") is started, the system will call getMathCaseImplementation() and
		 * get the team's implementation.  It then has a reference to their implementation so that in can
		 * call the corresponding methods as events happen.  This will make sense when you look at the Sample implementation.
		 */
		public abstract MathCase getMathCaseImplementation();
		
		static interface MathCase {
			
			void addVariables(IJobSetup setup);
			
			void initializeAlgo(Map<String, String> variables, IDB dataBase);
			
			void newAdminMessage(AdminMessage msg);
			
			void newForecastMessage(ForecastMessage msg);
			
		}
		
		public static enum MsgType {
			VEGA,
			GAMMA,
			DELTA
		}
		
		static class AdminMessage {
			
			public final MsgType type;
			public final int magnitude;
			
			private AdminMessage(MsgType type, int magnitude) {
				this.type = type;
				this.magnitude = magnitude;
			}
			
		}
		
		static class ForecastMessage {
			
			public final MsgType type;
			public final int magnitude;
			
			private ForecastMessage(MsgType type, int magnitude) {
				this.type = type;
				this.magnitude = magnitude;
			}
			
		}
		
		// ----------- Handle System Events and Translate to Case Interface Methods ---------------
		
		private IDB teamDB;
		private MathCase implementation;
		
		
		/*
		 * Freeway has its own events that are likely too complex for the student's to work out in one month.
		 * So what we do here is translate from the system events in Freeway, to the interface methods we've defined above.
		 * 
		 * Because of this, this class, "AbstractMathCase" now has control of the flow and we are basically acting as a
		 * middle-man between the system and the team's implementation.  Thus, we get to do specialized things, like
		 * assume infinite liquidity, add new risk penalties, etc. that otherwise wouldn't exist in the system.
		 */
		
		/*
		 * Required freeway method.  The IJobSetup object is used to register variables
		 * for this particular job
		 */
		@Override
		public void install(IJobSetup setup) {
			setup.addVariable("Team_Code", "Team Code and product to trade", "string", "");
			getMathCaseImplementation().addVariables(setup);
		}

}
