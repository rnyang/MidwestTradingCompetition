import random
import csv


class marketmaker(object):
	def __init__(self):
		#starting market price
		self.market_price = 100
		#Cumulative Probability of starting at each state
		self.start_state = [0.5, 1] #P(S <= s_i) = [s_1, s_2]
		self.curr_state = [0,1][sum([i < random.random() for i in self.start_state])] #set current state from above probabilities
		def cs(a): #not used, but you can if you want to define pdf instead of cdf
			return([sum(a[:i+1]) for i in xrange(len(a))])
		#Transition probability from state to state:Tran_StateNumber = [To_state_1, To_state_2]
		self.tran_1 = [0.25, .75]
		self.tran_2 = [0.75, .25]
		self.trans = [cs(self.tran_1),cs(self.tran_2)] #since these lists need to be cdf. 
		#Emission (1) probability from each state
		self.E_StateNumber = [0, 1, -1, 3, -3, 5, -5, 10, -10]
		self.e_1 = [0.1, 0.2, 0.25, 0.7, 0.75, 0.8, 0.81, 0.9, 1]
		self.e_2 = [0.01, 0.02, 0.03, 0.1, 0.12, 0.45, 0.6, 0.91, 1]
		self.e = [self.e_1, self.e_2]
		#Emission (2) probability from each state
		self.Spread_StateNumber = [1, 3, 5]
		self.s_1 = [0.3, 0.6, 1]
		self.s_2 = [0.25, 0.5, 1]
		self.s = [self.s_1,self.s_2]
	def state_gen(self): #generate emissions from a given state
		[rand1, rand2] = [random.random(), random.random()] #create 2 random numbers
		self.market_price += self.E_StateNumber[sum([i < rand1 for i in self.e[self.curr_state]])]
		spread = self.Spread_StateNumber[sum([i < rand2 for i in self.s[self.curr_state]])]
		bid = self.market_price - spread
		ask = self.market_price + spread
		bid_ask = [bid, ask]
		return (bid_ask)
	#Generate data
	def data_gen( self, number_of_trades ):
		#Initalize bid_ask_list. (Initial state is already set from line 11)
		self.bid_ask_list = [] 
		t = 1
		while( t <= number_of_trades ):
			#1) Create new bid, ask and market price fluctuation: 
			self.bid_ask_list.append(self.state_gen()) #self.state_gen() returns a [bid,ask] tuple 
			#2) Switch states if applicable
			self.curr_state = [0,1][sum([i < random.random() for i in self.trans[self.curr_state]])] #since self.trans is [[s1->s1, s1->s2], [s2->s1, s2->s1]]
			print self.curr_state
			t += 1
	def write_csv(self, file_name):
		ofile = open( file_name, "wb" )
		writer = csv.writer( ofile )
		writer.writerow( ["Bid", "Ask"] )
		[ writer.writerow( x ) for x in self.bid_ask_list ]
		ofile.close()