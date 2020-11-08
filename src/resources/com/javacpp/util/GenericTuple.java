package com.javacpp.util;

public class GenericTuple<K, V> {

    private final K first;

    private final V second;

    public GenericTuple(K first, V second) {
        this.first = first;
        this.second = second;
    }

    public K getFirst() {
        return first;
    }

    public V getSecond() {
        return second;
    }
}
