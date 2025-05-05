package prizm.util;

import java.util.Map;

public interface ExtensibleMapObject<K, V> extends Map<K, V> {
    BoostMap.HandlerRemover putEventListener(BoostMap.FullEvent<K, V> event);

    BoostMap.HandlerRemover putEventListener(K key, BoostMap.LocalEvent<K, V> event);

    V fetch(K key);

    int getCapacity();

    void setCapacity(int capacity);

    long getLifetime();

    void setLifetime(long lifetime);

    void putAll(BoostMap.ValueLoader<K, V> feeder);

    BoostMap.HandlerRemover getUserCallBack();

    void add(ExtensibleMapObject<K, V> map, boolean overwrite);

    Map<K, V> sub(ExtensibleMapObject<K, V> map);

    void removeAll(Map<? extends K, ? extends V> m);
}