from __future__ import division
import optionsutil as op
import random
import matplotlib.pyplot as plt
import csv

'''Fixed options parameters'''
strikes = [100+10*x for x in range(5)]
r = .01
vol = 1/60

'''Generate schocastic prices for the underlying'''
price = []
price.append(120)     
sigma = 2
for x in range(60):
    price.append(random.normalvariate(price[x],sigma))

'''plot'''
plt.plot(price)
plt.show()

'''write underlying/options prices to csv file'''
f = open("data.csv","wb")
writer = csv.writer(f)
writer.writerow(["Underlying Price", "Front 100", 110,120,130,140,"Back 100",110,120,130,140])
for x in range(60):
    row = []
    row.append(round(price[x],2))
    for K in strikes:
        row.append(round(op.Call(price[x],K,60-x,r,vol),2))
    for K in strikes:
        row.append(round(op.Call(price[x],K,90-x,r,vol),2))
    writer.writerow(row)
f.close()
