from __future__ import division
import numpy as np
import math
import scipy.stats as stats

'Returns the theoretical value of a call option'
def Call(S, K, t, r, vol):
    d_1 = (np.log(S / K) + (r + (vol)**2 / 2) * t) / (vol * np.sqrt(t))
    d_2 = (np.log(S / K) + (r - (vol)**2 / 2) * t) / (vol * np.sqrt(t))
    rv = stats.norm(0,1).cdf(d_1) * S - stats.norm(0,1).cdf(d_2) *K * math.exp(-r*t)
    return rv

'Returns the delta of a call option'
def Delta(S, K, t, r, vol):
    d_1 = (np.log(S / K) + (r + (vol)**2 / 2) * t) / (vol * np.sqrt(t))
    return stats.norm(0,1).cdf(d_1)

'Returns gamma'
def Gamma(S, K, t, r, vol):
    d_1 = (np.log(S / K) + (r + (vol)**2 / 2) * t) / (vol * np.sqrt(t))
    return stats.norm(0,1).cdf(d_1) / (S*vol*np.sqrt(t))

'Returns vega'
def Vega(S, K, t, r, vol):
    d_1 = (np.log(S / K) + (r + (vol)**2 / 2) * t) / (vol * np.sqrt(t))
    return stats.norm(0,1).cdf(d_1)*S*np.sqrt(t)

def Put(S, K, t, r, vol):
    d_1 = (np.log(S / K) + (r + (vol)**2 / 2) * t) / (vol * np.sqrt(t))
    d_2 = (np.log(S / K) + (r - (vol)**2 / 2) * t) / (vol * np.sqrt(t))
    rv = -stats.norm(0,1).cdf(-d_1) * S + stats.norm(0,1).cdf(-d_2) *K * math.exp(-r*t)
    return rv

'''for calls only'''
def ImpliedC(S, K, t, r, price):
    imply = 0.5
    veg = Vega(S, K, t, r, imply)
    y = Call(S, K, t, r, imply)
    while math.fabs(y - price) > 0.002:
        imply = imply - (y - price) / veg
        veg = Vega(S, K, t, r, imply)
        y = Call(S, K, t, r, imply)
    return imply

'''for puts only'''  
def ImpliedP(S, K, t, r, price):
    imply = 0.5
    veg = Vega(S, K, t, r, imply)
    y = Put(S, K, t, r, imply)
    while math.fabs(y - price) > 0.002:
        imply = imply - (y - price) / veg
        veg = Vega(S, K, t, r, imply)
        y = Put(S, K, t, r, imply)
    return imply
'''
r =.005
yhoo = 39.52
strike = 40
t = 3/365
x = ImpliedC(yhoo,strike,t,r,.29)
print x

d = Delta(yhoo,strike,t,r,x)
print d
'''






