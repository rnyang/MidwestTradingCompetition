package org.chicago.cases;

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IJobSetup;

public class AbstractExchangeArbCase extends AbstractJob {
	
	/*
	 * Required freeway method.  The IJobSetup object is used to register variables
	 * for this particular job
	 */
	@Override
	public void install(IJobSetup setup) {}

}
