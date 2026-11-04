# Standalone shape check; uses only Python stdlib.
# Test the XY polygon that also defines the Java rift outline.
from math import sin, cos, pi
def shape(k, n):
    a=2*pi*k/n
    return .91+.050*sin(5*a+.7)+.033*sin(11*a+1.8)+.022*cos(17*a+2.1)
def orient(a,b,c):
    return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
def crosses(a,b,c,d):
    return orient(a,b,c)*orient(a,b,d)<0 and orient(c,d,a)*orient(c,d,b)<0
for n in (40,56):
    poly=[(shape(k,n)*cos(2*pi*k/n),shape(k,n)*sin(2*pi*k/n)) for k in range(n)]
    for i in range(n):
        for j in range(i+2,n):
            if i==0 and j==n-1: continue
            assert not crosses(poly[i],poly[(i+1)%n],poly[j],poly[(j+1)%n]), (n,i,j)
    print("Non-self-intersecting rift outline:",n,"vertices")
