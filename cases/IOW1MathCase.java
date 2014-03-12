import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.chicago.cases.AbstractMathCase;
import org.chicago.cases.AbstractMathCase.MathCase;

import com.optionscity.freeway.api.IDB;
import com.optionscity.freeway.api.IJobSetup;

public class IOW1MathCase extends AbstractMathCase implements MathCase {

	static ArrayList<Integer> changes = new ArrayList<Integer>();
	static ArrayList<Integer> prices = new ArrayList<Integer>();

	String c = "";

	double totalPurchased = 0;
	double totalSold = 0;
	int position = 0;
	int numBull = 0;
	int numBear = 0;

	public int newBidAsk(double bid, double ask) {
		prices.add((int) (bid + ask) / 2);
		if (prices.size() > 1) {
			int change = prices.get(prices.size() - 1) - prices.get(prices.size() - 2);
			if (change > 0) {
				changes.add(0);
				c += "0";
			} else if (change < 0) {
				changes.add(1);
				c += "1";
			} else {
				c += "-";
			}
			HMM t = train(changes.size());
			int pred = t.getPred();
			if (pred == 0) {
				numBear = 0;
				if (numBull > 1 / (1 - t.stateProbs[0][0])) {
					return -1;
				} else {
					numBull++;
					return 1;
				}
			} else {
				numBull = 0;
				if (numBear > 1 / (1 - t.stateProbs[1][1])) {
					return 1;
				} else {
					numBear++;
					return -1;
				}

			}
		}
		return 0;
	}

	static class HMM {
		double[] pi = { .5, .5 };
		double[][] stateProbs = { { .6, .4 }, { .4, .6 } };
		double[][] emissionProbs = { { .8, .2 }, { .2, .8 } };
		List<Integer> observedChanges;
		double[][] alpha;

		int getPred() {
			if (alpha == null)
				return 0;
			if (alpha[0][alpha[0].length - 1] > alpha[1][alpha[0].length - 1])
				return 0;
			else
				return 1;
		}

		public HMM() {

		}

		HMM(double[] pi, double[][] stateProbs, double[][] emissionProbs, List<Integer> observedChanges) {
			this.pi = pi;
			this.stateProbs = stateProbs;
			this.emissionProbs = emissionProbs;
			this.observedChanges = observedChanges;
		}

		static HMM generateWithList(List<Integer> observedChanges) {
			double[] pi = { .5, .5 };
			double a = .5 + .5 * Math.random();
			double b = .5 + .5 * Math.random();
			double c = .5 + .5 * Math.random();
			double d = .5 + .5 * Math.random();

			double[][] stateProbs = { { a, 1 - a }, { 1 - b, b } };
			double[][] emissionProbs = { { c, 1 - c }, { 1 - d, d } };
			return new HMM(pi, stateProbs, emissionProbs, observedChanges);
		}

	}

	HMM train(int lag) {
		List<Integer> s1 = changes.size() > (lag - 1) ? changes.subList(changes.size() - lag, changes.size()) : changes;
		int S = s1.size();
		double bull = 0;
		double bear = 0;
		HMM best = new HMM();
		double bestScore = 0;
		for (int zz = 0; zz < 10; zz++) {
			HMM current = HMM.generateWithList(s1);
			for (int it = 0; it < 15; it++) {
				double[] alphaNorm = new double[S];
				double[][] alpha = new double[2][S];
				alpha[0][0] = current.emissionProbs[0][s1.get(0)] * current.pi[0];
				alpha[1][0] = current.emissionProbs[1][s1.get(0)] * current.pi[1];
				alphaNorm[0] = alpha[0][0] + alpha[1][0];
				alpha[0][0] = alpha[0][0] / alphaNorm[0];
				alpha[1][0] = alpha[1][0] / alphaNorm[0];
				for (int i = 1; i < S; i++) {
					double tmp = 0;
					for (int k = 0; k < 2; k++) {
						double z = current.emissionProbs[k][s1.get(i)] * (alpha[0][i - 1] * current.stateProbs[0][k] + alpha[1][i - 1] * current.stateProbs[1][k]);
						tmp += z;
						alpha[k][i] = z;
					}
					alphaNorm[i] = 1 / tmp;
					alpha[0][i] = alpha[0][i] / tmp;
					alpha[1][i] = alpha[1][i] / tmp;
				}
				double[][] beta = new double[2][S];
				beta[0][S - 1] = 1;
				beta[1][S - 1] = 1;
				for (int i = S - 2; i >= 0; i--) {
					for (int k = 0; k < 2; k++) {
						double z = (current.stateProbs[k][0] * current.emissionProbs[0][s1.get(i + 1)] * beta[0][i + 1])
								+ (current.stateProbs[k][1] * current.emissionProbs[1][s1.get(i + 1)] * beta[1][i + 1]);
						beta[k][i] = alphaNorm[i + 1] * z;
					}
				}

				double[][] gamma = new double[2][S];
				for (int i = 0; i < S; i++) {
					double denom = (alpha[0][i] * beta[0][i]) + (alpha[1][i] * beta[1][i]);
					gamma[0][i] = (alpha[0][i] * beta[0][i]) / denom;
					gamma[1][i] = (alpha[1][i] * beta[1][i]) / denom;
				}

				double[][][] digamma = new double[2][2][S - 1];
				for (int i = 0; i < S - 1; i++) {
					digamma[0][0][i] = (gamma[0][i] * current.stateProbs[0][0] * current.emissionProbs[0][s1.get(i + 1)] * alphaNorm[i + 1] * beta[0][i + 1]) / beta[0][i + 1];
					digamma[0][1][i] = (gamma[0][i] * current.stateProbs[0][1] * current.emissionProbs[1][s1.get(i + 1)] * alphaNorm[i + 1] * beta[1][i + 1]) / beta[0][i + 1];
					digamma[1][0][i] = (gamma[1][i] * current.stateProbs[1][0] * current.emissionProbs[0][s1.get(i + 1)] * alphaNorm[i + 1] * beta[0][i + 1]) / beta[1][i + 1];
					digamma[1][1][i] = (gamma[1][i] * current.stateProbs[1][1] * current.emissionProbs[1][s1.get(i + 1)] * alphaNorm[i + 1] * beta[1][i + 1]) / beta[1][i + 1];
				}

				current.pi[0] = gamma[0][0];
				current.pi[1] = gamma[1][0];
				for (int ai = 0; ai < 2; ai++) {
					for (int aj = 0; aj < 2; aj++) {
						double num = 0;
						double denom = 0;
						for (int t = 0; t < S - 1; t++) {
							num += digamma[ai][aj][t];
							denom += digamma[ai][0][t] + digamma[ai][1][t];
						}
						current.stateProbs[ai][aj] = num / denom;
					}
				}

				for (int state = 0; state < 2; state++) {
					for (int ob = 0; ob < 2; ob++) {
						double num = 0;
						double denom = 0;
						for (int t = 0; t < S - 1; t++) {
							denom += gamma[state][t];
							if (s1.get(t) == ob)
								num += gamma[state][t];
						}
						current.emissionProbs[state][ob] = num / denom;
					}
				}
				double ll = logLikelihood(alphaNorm);
				if (ll < bestScore) {
					bestScore = ll;
					best = new HMM(clone(current.pi), clone(current.stateProbs), clone(current.emissionProbs), current.observedChanges);
					best.alpha = alpha;
				}
				if (alpha[0][S - 1] > alpha[1][S - 1])
					bull += 1;
				else
					bear += 1;
			}
		}
		return best;
	}

	double[][] clone(double[][] orig) {
		double[][] n = new double[orig.length][orig[0].length];
		for (int i = 0; i < orig.length; i++) {
			n[i] = clone(orig[i]);
		}
		return n;
	}

	double[] clone(double[] orig) {
		double[] n = new double[orig.length];
		for (int i = 0; i < orig.length; i++) {
			n[i] = orig[i];
		}
		return n;
	}

	double logLikelihood(double[] norms) {
		double T = 0;
		for (int i = 0; i < norms.length; i++) {
			T += -Math.log(norms[i]);
		}
		return T;
	}

	static String matrixToString(double[][] foo) {
		String r = "";
		for (int i = 0; i < foo.length; i++) {
			r += Arrays.toString(foo[i]);
		}
		return r;
	}

	static String matrixToString(double[] foo) {
		return Arrays.toString(foo);
	}

	public MathCase getMathCaseImplementation() {
		return this;
	}

	@Override
	public void addVariables(IJobSetup arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void initializeAlgo(IDB arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void orderFilled(int arg0, double arg1) {
		position += arg0;
		if (arg0 > 0) {
			totalPurchased += arg0 * arg1;
		} else {
			totalSold += arg0 * arg1;
		}
		double holdings = position * prices.get(prices.size() - 1);
		log("Total Purchased: " + totalPurchased + " Total Sold: " + totalSold + " Total Holdings: " + position * prices.get(prices.size() - 1));
		log("" + (totalPurchased + totalSold + holdings) / totalPurchased);
	}

}