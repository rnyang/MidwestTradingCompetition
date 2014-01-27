import numpy as np
import matplotlib.pyplot as ppt

# 0. Time periods
T = 100

# 1. Evolving True Value

# AR(1) Process
value = np.zeros(T)
valuee = np.random.normal(0, 1.5, T)
value[0] = 100.
for i in xrange(1, T):
    value[i] = value[i-1] + valuee[i]

# 2. Evolving Deviations from the True Value
dev1 = np.zeros(T)
deve1 = np.random.normal(0, 0.5, T)
dev2 = np.zeros(T)
deve2 = np.random.normal(0, 0.5, T)

dev1[0] = 0
dev2[0] = 0

for i in xrange(1, T):
	dev1[i] = 1.0 * ( deve1[i] + 0.5 * deve1[i-1] )
	dev2[i] = 1.0 * ( deve2[i] + 0.5 * deve2[i-1] )

ex1 = value + dev1
ex2 = value + dev2

# 3. Evolving Spreads
# (spreads widen when midpoint moves)
spread1 = np.ones(T)
spreade1 = np.random.normal(1.0, 0.3, T)
spread2 = np.ones(T)
spreade2 = np.random.normal(1.0, 0.3, T)

for i in xrange(2,T):
	spread1[i] = 0.0 + 0.1 * np.abs(ex1[i] - ex1[i-1]) \
	                 + 0.1 * np.abs(ex1[i] - ex1[i-2]) \
	                 + np.abs(spreade1[i])
	spread2[i] = 0.0 + 0.1 * np.abs(ex2[i] - ex2[i-1]) \
	                 + 0.1 * np.abs(ex1[i] - ex1[i-2]) \
	                 + np.abs(spreade2[i])

ex1b = ex1 - spread1
ex1a = ex1 + spread1

ex2b = ex2 - spread2
ex2a = ex2 + spread2

# 4. Round to nearest 0.25
roundQtr = lambda x: [round(i / 0.25) * 0.25 for i in x]

ex1b = roundQtr(ex1b)
ex1a = roundQtr(ex1a)
ex2a = roundQtr(ex2a)
ex2b = roundQtr(ex2b)

grid = np.array(range(T))

# 5. Plot (optional)

# ppt.clf()

# ppt.plot(grid, value, color='purple')
# ppt.plot(grid, ex1, color='purple')
# ppt.plot(grid, ex2, color='purple')

# ppt.scatter(grid, ex1b, color='blue')
# ppt.scatter(grid, ex1a, color='blue')

# ppt.scatter(grid, ex2b, color='red')
# ppt.scatter(grid, ex2a, color='red')

# ppt.grid(b=True, which='both')

# ppt.show()

# 6. Write to File

# Exchange1 = ROBOT
# Exchange2 = SNOW

# Example
# S,1.39058E+12,ROBOT;98.0;102.0;SNOW;100.0;104.0
entries = [("S, " + i + ",ROBOT;" + ex1b[i] + ";" + ex1a[i] + \
                        ";SNOW;" + ex2b[i] + ";" + ex2a[i] + "\n") \
                        for i in range(T)]

with open('foo.csv') as openFile:
	for entry in entries:
		openFile.write(entry)
