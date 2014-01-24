import random
import csv

#Probability of starting at each state
#S = [S1, S2]
s = [0.5, 0.5]

#Transition probability from state to state
#Tran_StateNumber = [To_state_1, To_state_2]
tran_1 = [0.25, 0.75]
tran_2 = [0.75, 0.25]

#Emission (1) probability from each state
#E_StateNumber = [0, 1, -1, 3, -3, 5, -5, 10, -10]
e_1 = [0.1, 0.3, 0.15, 0.15, 0.05, 0.05, 0.01, 0.09, 0.1]
e_2 = [0.01, 0.01, 0.01, 0.07, 0.3, 0.05, 0.45, 0.01, 0.09]

#Emission (2) probability from each state
#Spread_StateNumber = [1, 3, 5]
s_1 = [0.3, 0.3, 0.4]
s_2 = [0.25, 0.25, 0.5]

#Check_list
check_list = [s, tran_1, tran_2, e_1, e_2, s_1, s_2]

#Used to check whether all probabilities entered sum up to 1
def check( check_list ):
    for i in check_list:
        sum = 0.0
        for j in i:
            sum = sum + j
        if round(sum, 6) == 1:
            print "Probability sum equal to 1"
            continue
        else:
            print "Probability sum not equal to 1. Please check your number!"
            break
    return 

#Generate new bid ask in from given state
def state_gen( emission_1_prob, emission_2_prob, market_price ):
    rand = random.random()
    e_1 = emission_1_prob
    e_2 = emission_2_prob
    if rand < e_1[0]:
        market_price = market_price 
    elif rand >= e_1[0] and rand < e_1[1]:
        market_price = market_price + 1
    elif rand >= e_1[1] and rand < e_1[2]:
        market_price = market_price - 1
    elif rand >= e_1[2] and rand < e_1[3]:
        market_price = market_price + 3
    elif rand >= e_1[3] and rand < e_1[4]:
        market_price = market_price - 3
    elif rand >= e_1[4] and rand < e_1[5]:
        market_price = market_price + 5
    elif rand >= e_1[5] and rand < e_1[6]:
        market_price = market_price - 5
    elif rand >= e_1[6] and rand < e_1[7]:
        market_price = market_price + 10
    else:
        market_price = market_price - 10
    rand = random.random()
    if rand < e_2[0]:
        bid = market_price - 1
        ask = market_price + 1
    elif rand >= e_2[0] and rand < e_2[1]:
        bid = market_price - 3
        ask = market_price + 3
    else:
        bid = market_price - 5
        ask = market_price + 5
    bid_ask = [bid, ask]
    return (bid_ask, market_price)

#Generate data
def data_gen( check_list, market_price, number_of_trades ):
    #Check check_list first
    check( check_list )
    #Initalize bid_ask_list
    bid_ask_list = []
    #Generate the first set of data given initial distribution 
    rand = random.random()
    if rand < check_list[0][0]:
        result = state_gen( check_list[3], check_list[5], market_price )
        bid_ask_list.append( result[0] )
        market_price = result[1]
        state = 1
    else:
        result = state_gen( check_list[3], check_list[5], market_price )
        bid_ask_list.append( result[0] )
        market_price = result[1]
        state = 2
    #Generate the rest of data 
    t = 1
    while( t < number_of_trades ):
        #Check which state the program is in and update tran
        if state == 1:
            tran = check_list[1]
        else:
            tran = check_list[2]
        rand = random.random()
        if rand < tran[0]:
            result = state_gen( check_list[3], check_list[5], market_price )
            bid_ask_list.append( result[0] )
            market_price = result[1]
            state = 1
        else:
            result = state_gen( check_list[3], check_list[5], market_price )
            bid_ask_list.append( result[0] )
            market_price = result[1]
            state = 2
        t = t + 1
    return bid_ask_list

#Write into csv
def write_csv( bid_ask_list ):
    ofile = open( '/Users/hqizhen/Desktop/case3data.csv', "wb" )
    writer = csv.writer( ofile )
    writer.writerow( ["Bid", "Ask"] )
    [ writer.writerow( x ) for x in bid_ask_list ]
    ofile.close()
    return 
    
    





