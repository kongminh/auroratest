package aurora;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extends LinkedHashMap to provide additional functionality.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class LinkedHashMapExtend<K, V> extends LinkedHashMap<K, V> {

  private final int capacity;
  private int countGetByIdHit;

  /**
   * Constructs a new, empty LinkedHashMapExtend instance with the specified initial capacity, load factor, and ordering mode.
   *
   * @param initialCapacity the initial capacity
   * @param loadFactor      the load factor
   * @param accessOrder     the ordering mode - true for access-order, false for insertion-order
   */
  public LinkedHashMapExtend(int initialCapacity, float loadFactor, boolean accessOrder) {
      super(initialCapacity, loadFactor, accessOrder);
      this.capacity = initialCapacity;
      countGetByIdHit = 0;
  }

  /**
   * Returns true if this map should remove its eldest entry.
   *
   * @param eldest the least recently accessed entry
   * @return true if the eldest entry should be removed
   */
  @Override
  protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
      return size() > this.capacity;
  }

  /**
   * Retrieves the capacity of the LinkedHashMapExtend.
   *
   * @return the capacity of the LinkedHashMapExtend
   */
  public int getCapacity() {
    return this.capacity;
  }

  /**
   * Retrieves the count of how many times the get method has been called successfully.
   *
   * @return the count of successful get method invocations
   */
  public int getCountGetByIdHit() {
      return countGetByIdHit;
  }

  /**
   * Retrieves the value to which the specified key is mapped, incrementing the count if the key is found.
   *
   * @param key the key whose associated value is to be returned
   * @return the value to which the specified key is mapped, or null if this map contains no mapping for the key
   */
  @Override
  public V get(Object key) {
      V value = super.get(key);
      if (value != null) {
          countGetByIdHit++;
      }
      return value;
  }
}
