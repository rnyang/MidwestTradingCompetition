

import com.optionscity.freeway.api.AbstractJob;
import com.optionscity.freeway.api.IContainer;
import com.optionscity.freeway.api.IJobSetup;
import com.optionscity.freeway.api.messages.Signal;
import com.optionscity.freeway.api.services.IPlaybackService.ISignalProcessor;

public class SignalReceivingJob extends AbstractJob {

	public void install(IJobSetup arg0) {}
	
	public void begin(IContainer container) {
		super.begin(container);
		container.subscribeToSignals();
		container.getPlaybackService().register(new SampleSignalProcessor());
	}

	@Override
	public void onSignal(Signal signal) {
		log("clz=" + signal.clazz + ", msg=" + signal.message);
	}
	
	public static class SampleSignalProcessor implements ISignalProcessor {

		// Not necessary for import
		public String asString(Signal signal) {return "";}

		// Used to contruct an object from the String
		public Signal fromString(String data) {
			SampleSignal signal = new SampleSignal(data);
			return signal;
		}
		
	}
	
}
