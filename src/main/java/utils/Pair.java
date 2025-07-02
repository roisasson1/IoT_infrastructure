package utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class Pair<K,V> implements Map.Entry<K,V> {
    private final K key;
    private V value;

    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
    }

    @Override
    public V setValue(V v) {
        V old = value;
        value = v;
        return old;
    }

    public static <K, V> Pair<V, K> swap(Pair<K, V> pair) {
        return new Pair<>(pair.value, pair.key);
    }

    public static <E extends Comparable<E>> Pair<E, E> minMax(ArrayList<E> collection) {
        if (collection == null || collection.isEmpty()) {
            return null;
        }

        E min = Collections.min(collection);
        E max = Collections.max(collection);

        return new Pair<>(min, max);
    }

    public static <K, V> Pair<K, V> of(K key, V value) {
        return new Pair<>(key, value);
    }
}
