package gateway.RPS.factory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class Factory<K, D, T> {
    private final Map<K, Function<D, ? extends T>> map = new HashMap<>();

    public T create(K key, D data) {
        return (key != null && map.containsKey(key)) ? map.get(key).apply(data) : null;
    }

    public void add(K key, Function<D, ? extends T> func) {
        map.put(key, func);
        System.out.println(key + " command added");
    }
}