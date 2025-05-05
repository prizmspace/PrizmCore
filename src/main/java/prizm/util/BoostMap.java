package prizm.util;

import java.util.Map.Entry;
import java.util.*;

public class BoostMap<K,V> extends LinkedHashMap<K, V> implements ExtensibleMapObject<K, V> {

    public interface ValueMarker {
    }
    public interface HandlerRemover {
        void doRemove();
    }
    public interface LocalEvent<K, V> {
        void onAdd(K key, V value);
        void onRemove(K key, V value);
        void onChange(K key, V oldValue, V newValue);
    }

    public interface FullEvent<K, V> extends LocalEvent<K, V> {
        void onClear();
    }

    private interface EventRemover {
        void remove();
    }

    public interface ValueLoader<K, V> {
        Map<K, V> loadValues();
    }

    private void clearAllEventRemovers() {
        for (EventRemover eventRemover : eventRemovers) {
            eventRemover.remove();
        }
        eventRemovers.clear();
        eventRemovers = null;
    }

    private void eventValueChanged(K key, V oldValue, V newValue) {
        if (eventRemovers != null) clearAllEventRemovers();
        if (!super.containsKey(key)) return;
        if ((events != null)&&(!events.isEmpty())) {
            for (FullEvent<K, V> event : events) {
                event.onChange(key, oldValue, newValue);
            }
        }
        if ((keyEvents != null)&&(keyEvents.get(key) != null)&&(!keyEvents.get(key).isEmpty())) {
            for (LocalEvent<K, V> event : keyEvents.get(key)) {
                event.onChange(key, oldValue, newValue);
            }
        }
    }


    private void eventRemoved(K key, V value) {
        if (eventRemovers != null) clearAllEventRemovers();
        if (!super.containsKey(key)) return;
        if ((events != null)&&(!events.isEmpty())) {
            for (FullEvent<K, V> event : events) {
                event.onRemove(key, value);
            }
        }
        if ((keyEvents != null)&&(keyEvents.get(key) != null)&&(!keyEvents.get(key).isEmpty())) {
            for (LocalEvent<K, V> event : keyEvents.get(key)) {
                event.onRemove(key, value);
            }
        }
    }

    private void eventAdded(K key, V value) {
        if (eventRemovers != null) clearAllEventRemovers();
        if ((events != null)&&(!events.isEmpty())) {
            for (FullEvent<K, V> event : events) {
                event.onAdd(key, value);
            }
        }
        if ((keyEvents != null)&&(keyEvents.get(key) != null)&&(!keyEvents.get(key).isEmpty())) {
            for (LocalEvent<K, V> event : keyEvents.get(key)) {
                event.onAdd(key, value);
            }
        }
    }

    private void eventCleared() {
        if (eventRemovers != null) clearAllEventRemovers();
        if ((events != null)&&(!events.isEmpty())) {
            for (FullEvent<K, V> event : events) {
                event.onClear();
            }
        }
        if ((keyEvents != null)&&(!keyEvents.isEmpty())) {
            for (Entry<K, ArrayList<LocalEvent<K, V>>> entry : keyEvents.entrySet()) {
                if (!_containsKey(entry.getKey())) continue;
                for (LocalEvent<K, V> event : entry.getValue()) {
                    event.onRemove(entry.getKey(), super.get(entry.getKey()));
                }
            }
        }

    }

    private LinkedHashMap<K, Long> keyMap = new LinkedHashMap<K, Long>();
    private ArrayList<FullEvent<K, V>> events = null;
    private HashMap<K, ArrayList<LocalEvent<K, V>>> keyEvents = null;
    private long lifetime = -1;
    private int capacity = -1;
    private HandlerRemover eventRegistration = null;
    private boolean blockCapacity = false;
    private boolean blockLifetime = false;
    private ArrayList<EventRemover> eventRemovers = null;

    private void accessKey(K key) {
        if (key == null) return;
        if (keyMap.containsKey(key)) {
            keyMap.remove(key);
            keyMap.put(key, System.currentTimeMillis());
        }
    }

    private void recycle() {
        ArrayList<K> arrToRemove = new ArrayList<K>();

        if ((keyMap == null)||(keyMap.isEmpty())) return;
        long current = System.currentTimeMillis();
        if (this.lifetime != -1) {
            for (Entry<K, Long> entry : keyMap.entrySet() ) {
                if ((entry.getValue()+this.lifetime)<current) {
                    arrToRemove.add(entry.getKey());
                } else break;
            }
            for (K key : arrToRemove) {
                _remove(key);
            }
        }
    }

    private void volume() {
        ArrayList<K> arrToRemove = new ArrayList<K>();

        if ((keyMap == null)||(keyMap.isEmpty())) return;
        int toRemove = 0;
        if (this.capacity != -1) {
            if (keyMap.size() > this.capacity) toRemove = keyMap.size() - this.capacity;
            for (K key : keyMap.keySet()) {
                if (toRemove-- == 0) break;
                arrToRemove.add(key);
            }
            for (K key : arrToRemove) {
                _remove(key);
            }
        }
    }

    private void death() {
        recycle();
        volume();
    }

    @Override
    public final HandlerRemover putEventListener(final FullEvent<K,V> event) {
        if (events == null) {
            events = new ArrayList<FullEvent<K, V>>();
        }
        events.add(event);
        HandlerRemover registration = new HandlerRemover() {

            private boolean used = false;

            @Override
            public void doRemove() {
                if (used) return;
                used = true;
                EventRemover eventRemover = new EventRemover() {

                    @Override
                    public void remove() {
                        events.remove(event);
                    }
                };
                if (eventRemovers == null) eventRemovers = new ArrayList<EventRemover>();
                eventRemovers.add(eventRemover);
            }
        };
        return registration;
    }

    @Override
    public HandlerRemover putEventListener(final K key, final LocalEvent<K, V> event) {
        if (keyEvents == null) keyEvents = new HashMap<K, ArrayList<LocalEvent<K, V>>>();
        if (!keyEvents.containsKey(key)) keyEvents.put(key, new ArrayList<LocalEvent<K, V>>());
        keyEvents.get(key).add(event);
        HandlerRemover registration = new HandlerRemover() {

            private boolean used = false;

            @Override
            public void doRemove() {
                if (used) return;
                used = true;
                EventRemover eventRemover = new EventRemover() {
                    @Override
                    public void remove() {
                        if ((keyEvents == null)||(keyEvents.get(key) == null)||(keyEvents.get(key).isEmpty())) return;
                        keyEvents.get(key).remove(event);
                        if (keyEvents.get(key).isEmpty()) keyEvents.remove(key);
                    }
                };
                if (eventRemovers == null) eventRemovers = new ArrayList<EventRemover>();
                eventRemovers.add(eventRemover);
            }
        };
        return registration;
    }

    @Override
    public void clear() {
        eventCleared();
        super.clear();
    }

    private boolean _containsKey(K key) {
        return super.containsKey(key);
    }

    @Override
    public boolean containsKey(Object key) {
        recycle();
        return super.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        recycle();
        return super.containsValue(value);
    }

    @Override
    public V get(Object key) {
        recycle();
        accessKey((K)key);
        return super.get(key);
    }

    @Override
    public V fetch(K key) {
        recycle();
        return super.get(key);
    }

    @Override
    public boolean isEmpty() {
        recycle();
        return super.isEmpty();
    }

    @Override
    public Set<K> keySet() {
        recycle();
        return super.keySet();
    }

    @Override
    public V put(K key, V value) {
        if (!blockLifetime) recycle();
        if (key == null) return null;
        V ret =  super.get(key);

        if (ret != null) {
            if ((ret instanceof BoostMap.ValueMarker) && (value instanceof BoostMap.ValueMarker)) {
                super.put(key, value);
                if (!((ValueMarker) ret).equals(value)) {
                    eventValueChanged(key, ret, value);
                }
            } else {
                eventRemoved(key, ret);
                super.put(key, value);
                eventAdded(key, value);
            }
        } else {
            super.put(key, value);
            eventAdded(key, value);
        }
        if (!blockCapacity) volume();
        return ret;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        recycle();
        blockCapacity = true; blockLifetime = true;
        super.putAll(m);
        blockCapacity = false; blockLifetime = false;
        volume();
    }

    @Override
    public void removeAll(Map<? extends K, ? extends V> m) {
        if ((m == null)||(m.isEmpty())) return;
        for (K entry : m.keySet()) {
            remove(entry);
        }
    }

    @Override
    public void putAll(ValueLoader<K, V> feeder) {
        putAll(feeder.loadValues());
    }

    private V _remove(Object key) {
        eventRemoved((K)key, super.get((K)key));
        return super.remove((K)key);
    }

    @Override
    public V remove(Object key) {
        recycle();
        return _remove(key);
    }

    @Override
    public int size() {
        recycle();
        return super.size();
    }

    @Override
    public Collection<V> values() {
        recycle();
        return super.values();
    }

    private BoostMap(int initialCapacity, float loadFactor, boolean accessOrder) {
        super(initialCapacity, loadFactor, accessOrder);
    }

    private BoostMap(Map<? extends K, ? extends V> m) {
        super(m);
    }

    private BoostMap(int initialCapacity) {
        super(initialCapacity);
    }

    private BoostMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public long getLifetime() {
        return lifetime;
    }

    @Override
    public void setLifetime(long lifetime) {
        this.lifetime = lifetime;
    }

    public BoostMap() {
        super();
        FullEvent<K,V> event = new FullEvent<K,V>() {

            @Override
            public void onAdd(K key, V value) {
                keyMap.put(key, System.currentTimeMillis());
            }

            @Override
            public void onRemove(K key, V value) {
                keyMap.remove(key);
            }

            @Override
            public void onClear() {
                keyMap.clear();
            }

            @Override
            public void onChange(K key, V oldValue, V newValue) {
            }

        };
        eventRegistration = putEventListener(event);
    }

    public BoostMap(int capacity, long lifetime) {
        this();
        this.capacity = capacity;
        this.lifetime = lifetime;
    }

    private HandlerRemover userCallBack = null;

    public BoostMap(FullEvent callBack) {
        this();
        userCallBack = putEventListener(callBack);
    }

    public BoostMap(int capacity, long lifetime, FullEvent callBack) {
        this(capacity, lifetime);
        userCallBack = putEventListener(callBack);
    }

    @Override
    public HandlerRemover getUserCallBack() {
        return userCallBack;
    }

    @Override
    public void add(ExtensibleMapObject<K, V> map, boolean overwrite) {
        if ((map == null)||(map.isEmpty())) return;
        for (Entry<K, V> entry : map.entrySet()) {
            if (super.containsKey(entry.getKey())) {
                if (overwrite) {
                    V oldValue = super.get(entry.getKey());
                    V newValue = entry.getValue();
                    if ((oldValue instanceof BoostMap.ValueMarker)&&(newValue instanceof BoostMap.ValueMarker)) {
                        if (!((ValueMarker)oldValue).equals(newValue)) {
                            eventValueChanged(entry.getKey(), super.put(entry.getKey(), newValue), newValue);
                        }
                    } else {
                        remove(entry.getKey());
                        put(entry.getKey(), entry.getValue());
                    }
                }
            } else {
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public Map<K, V> sub(ExtensibleMapObject<K, V> map) {
        HashMap<K, V> resultMap = new HashMap<K, V>();
        if ((map == null)||(map.isEmpty())) {
            resultMap.putAll(this);
            return resultMap;
        }
        for (Entry<K, V> entry : entrySet()) {
            if (map instanceof BoostMap) {
                if (!((BoostMap<K,V>)map).containsKey(entry.getKey())) {
                    resultMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return resultMap;
    }
}