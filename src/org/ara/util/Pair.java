package org.ara.util;

public class Pair<A, B> 
{
    private A first;
    private B second;

    public Pair(A first, B second) 
    {
        this.first = first;
        this.second = second;
    }

    public boolean equals(Object other) {
        if (other instanceof Pair) {
                Pair<A,B> otherPair = (Pair<A,B>) other;
                return 
                ((  this.first == otherPair.first ||
                        ( this.first != null && otherPair.first != null &&
                          this.first.equals(otherPair.first))) &&
                 (      this.second == otherPair.second ||
                        ( this.second != null && otherPair.second != null &&
                          this.second.equals(otherPair.second))) );
        }

        return false;
    }

    public String toString()
    { 
           return "(" + first + ", " + second + ")"; 
    }

    public A getFirst() {
        return first;
    }

    public void setFirst(A first) {
        this.first = first;
    }

    public B getSecond() {
        return second;
    }

    public void setSecond(B second) {
        this.second = second;
    }
}
