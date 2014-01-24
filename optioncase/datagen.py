from __future__ import division
import optionsutil as op
import random
import matplotlib.pyplot as plt
import csv
import numpy as np

'''Team Data'''
Teams = ["CHI1"]

'''Options parameters'''
K = [80,90,100,110,120]
r = .01

'''Generate schocastic prices for the underlying'''
def gen_COLD():
    COLD = []
    COLD.append(100)     
    sigma = 2
    for x in range(99):
        COLD.append(round(random.normalvariate(COLD[x],sigma),2))
    plt.plot(COLD)
    plt.show()
    return COLD

def gen_COLD_csv(COLD):
    f = open("COLDdata.csv","wb")
    writer = csv.writer(f)
    for x in COLD:
        writer.writerow([x])
    f.close()

def realized_vol(COLD):
    log_COLD = [np.log(x) for x in COLD]
    quad_var = []
    for i in range(len(COLD)-1):
        quad_var.append((log_COLD[i+1]-log_COLD[i])**2)
    vol = np.sqrt(sum(quad_var))
    return vol

def read_COLD_csv(filename):
    f = open(filename,"rb")
    reader = csv.reader(f)
    COLD = [round(float(x[0]),2) for x in reader]
    return COLD

def gen_options(COLD,K,r,vol):
    option = []
    for i in range(len(COLD)):
        option.append([])
    for i in range(len(COLD)):
        for j in range(len(K)):
            option[i].append(round(op.Call(COLD[i],K[j],(99-i)/365.,r,vol[i]),2))
        for j in range(len(K)):
            option[i].append(round(op.Call(COLD[i],K[j],(129-i)/365.,r,vol[i]),2))
    return option

def gen_option_csv(option):
    f = open("optiondata.csv","wb")
    writer = csv.writer(f)
    for x in option:
        writer.writerow(x)
    f.close()

def gen_market(price):
    center = random.normalvariate(price,3/100*price)
    bid = center - price*3/100 - .01
    ask = center + price*3/100 +.01
    if bid < 0:
        ask = ask - bid
        bid = 0
    return (round(bid,2), round(ask,2))

def gen_optionmarket(option):
    optionmarket = []
    for x in option:
        optionmarket.append([gen_market(y) for y in x])
    return optionmarket

'''Returns the option greeks for a given time index t'''
def greeks(COLD,K,r,vol,t):
    delta = []
    gamma =[]
    vega = []
    for i in range(len(K)):
        delta.append(op.Delta(COLD[t],K[i],(99-t)/365.,r,vol[t]))
        gamma.append(op.Gamma(COLD[t],K[i],(99-t)/365.,r,vol[t]))
        vega.append(op.Vega(COLD[t],K[i],(99-t)/365.,r,vol[t]))
    for i in range(len(K)):
        delta.append(op.Delta(COLD[t],K[i],(129-t)/365.,r,vol[t]))
        gamma.append(op.Gamma(COLD[t],K[i],(129-t)/365.,r,vol[t]))
        vega.append(op.Vega(COLD[t],K[i],(129-t)/365.,r,vol[t]))
    print delta
    print gamma
    print vega

def master_data(COLD,optionmarket,Teams):
    f = open("masterdata.csv","wb")
    writer = csv.writer(f)  
    for i in range(len(COLD)):
        for team in Teams:
            row = ("T",i,"COLD~"+team,100000,COLD[i],100000,COLD[i])
            writer.writerow(row)
            for j in range(len(optionmarket[i])):
                row = ("T",i,"op" +str(j+1)+"~"+team,100000,optionmarket[i][j][0],100000,optionmarket[i][j][1])
                writer.writerow(row)
    f.close()
    
'''
COLD = gen_COLD()
gen_COLD_csv(COLD)
vol = realized_vol(COLD)
print vol
'''

COLD = read_COLD_csv("COLDdata.csv")
COLD1 = COLD[0:50]
COLD2 = COLD[50:]
vol = realized_vol(COLD)
vol = [vol for x in range(100)]
vol[0] = .1

option = gen_options(COLD,K,r,vol)
optionmarket = gen_optionmarket(option)
master_data(COLD,optionmarket,Teams)


