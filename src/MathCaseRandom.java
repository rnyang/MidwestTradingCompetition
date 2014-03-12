import java.util.Random;

import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;


public class MathCaseRandom extends AbstractMathCase implements MathCase {
	
	Random r = new Random();

	public void addVariables(IJobSetup setup) {}

	public void initializeAlgo(IDB dataBase) {}

	public int newBidAsk(double bid, double ask) {
		int size = r.nextInt(2);
		boolean purchase = r.nextBoolean();
		if (purchase)
			return size;
		else return 0;
	}

	public void orderFilled(int volume, double fillPrice) {}

	public MathCase getMathCaseImplementation() {
		return this;
	}

}
